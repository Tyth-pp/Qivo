package com.qivo;

import com.qivo.config.RedisConfig;
import com.qivo.entity.Product;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProjectNamingTests {

    @Test
    void mapperXmlNamespacesAndEntityTypesResolveAfterPackageRename() throws Exception {
        Configuration configuration = new Configuration();
        Resource[] resources =
                new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml");
        assertTrue(resources.length > 0);
        for (Resource resource : resources) {
            try (InputStream stream = resource.getInputStream()) {
                new XMLMapperBuilder(
                                stream,
                                configuration,
                                resource.toString(),
                                configuration.getSqlFragments())
                        .parse();
            }
        }
        assertTrue(configuration.hasStatement("com.qivo.mapper.ProductMapper.selectPage"));
        assertTrue(configuration.hasStatement("com.qivo.mapper.OrderMapper.selectById"));
        assertTrue(
                configuration
                        .getMappedStatement("com.qivo.mapper.ProductMapper.selectPage")
                        .getResultMaps()
                        .stream()
                        .allMatch(map -> map.getType() == Product.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void homepageCacheRoundTripsRenamedEntityTypesWithoutRedisConnection() {
        RedisTemplate<String, Object> template =
                new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));
        RedisSerializer<Object> serializer =
                (RedisSerializer<Object>) template.getValueSerializer();
        Product product = new Product();
        product.setId(1L);
        product.setName("栖物 Qivo");
        product.setPrice(new BigDecimal("99.00"));
        Map<String, Object> homepage = new HashMap<>();
        homepage.put("hotProducts", new ArrayList<>(List.of(product)));
        byte[] bytes = serializer.serialize(homepage);
        assertNotNull(bytes);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("com.qivo.entity.Product"));
        Map<?, ?> restored = (Map<?, ?>) serializer.deserialize(bytes);
        assertNotNull(restored);
        Product restoredProduct = (Product) ((List<?>) restored.get("hotProducts")).get(0);
        assertEquals(product.getId(), restoredProduct.getId());
        assertEquals(product.getPrice(), restoredProduct.getPrice());
    }
}
