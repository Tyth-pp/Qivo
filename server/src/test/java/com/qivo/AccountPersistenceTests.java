package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import com.qivo.cache.RedisStore;
import com.qivo.entity.User;
import com.qivo.exception.BusinessException;
import com.qivo.security.Passwords;
import com.qivo.security.SessionRegistry;
import com.qivo.service.AdminService;
import com.qivo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:account_settings;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
    "spring.sql.init.schema-locations=classpath:test-schema.sql,classpath:account-test-schema.sql"
})
@ActiveProfiles("test")
class AccountPersistenceTests {
    @Autowired JdbcTemplate db;
    @Autowired UserService users;
    @Autowired AdminService admins;
    @Autowired Passwords passwords;
    @MockitoBean RedisStore redis;
    @MockitoBean SessionRegistry sessions;

    @Test void selfProfileAndPasswordPersistWithoutChangingOtherAccountFields() {
        String old = passwords.encode("originalPassword9");
        db.update("INSERT INTO `user`(id,username,password,status,score) VALUES(100,'account-test',?,1,20)",old);
        db.update("INSERT INTO admin(id,username,password,status) VALUES(100,'admin-test',?,1)",old);
        String avatar = "/upload/avatars/2026/10/27/12345678-1234-1234-1234-123456789abc.png";
        User input = new User(); input.setId(100L); input.setAvatar(avatar); input.setScore(999); input.setPassword("injected");
        users.updateProfile(input);
        assertEquals(avatar,users.getById(100L).getAvatar());
        assertEquals(20,users.getById(100L).getScore());
        assertEquals(old,users.getById(100L).getPassword());
        input.setAvatar(""); users.updateProfile(input); assertEquals("",users.getById(100L).getAvatar());
        admins.updateProfile(100L,"新的显示名",avatar);
        assertEquals(avatar,admins.getById(100L).getAvatar());
        assertEquals(1,admins.getById(100L).getStatus());
        users.updatePassword(100L,"originalPassword9","changedPassword9");
        assertTrue(passwords.matches("changedPassword9",users.getById(100L).getPassword()));
        assertThrows(BusinessException.class,()->users.updatePassword(100L,"originalPassword9","anotherPassword9"));
        admins.updatePassword(100L,"originalPassword9","changedAdminPassword9");
        assertTrue(passwords.matches("changedAdminPassword9",admins.getById(100L).getPassword()));
        assertEquals("新的显示名",admins.getById(100L).getRealName());
    }
}
