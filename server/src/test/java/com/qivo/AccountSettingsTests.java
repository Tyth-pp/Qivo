package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.qivo.entity.User;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.AddressMapper;
import com.qivo.mapper.UserMapper;
import com.qivo.properties.FileUploadProperties;
import com.qivo.security.JwtTokenProvider;
import com.qivo.security.Passwords;
import com.qivo.security.SessionRegistry;
import com.qivo.service.impl.LocalFileStorageServiceImpl;
import com.qivo.service.impl.UserServiceImpl;
import com.qivo.utils.AvatarFiles;
import org.springframework.core.io.ClassPathResource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class AccountSettingsTests {
    @TempDir Path folder;
    @Test void avatarBytesAndPathsAreValidatedAndFilesUseIndependentNamespace() throws Exception {
        var config = new FileUploadProperties(); config.setPath(folder.toString()+"/"); config.setAllowTypes("png,jpg,webp");
        var storage = new LocalFileStorageServiceImpl(config);
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB),"png",output);
        String path = storage.uploadAvatar(new MockMultipartFile("file","avatar.png","image/png",output.toByteArray()));
        AvatarFiles.validatePath(path); assertTrue(Files.exists(folder.resolve(path.substring("/upload/".length()))));
        assertThrows(BusinessException.class, () -> storage.uploadAvatar(new MockMultipartFile("file","x.jpg","image/jpeg",output.toByteArray())));
        assertThrows(BusinessException.class, () -> storage.uploadAvatar(new MockMultipartFile("file","x.png","image/png","<svg/>".getBytes())));
        assertThrows(BusinessException.class, () -> storage.uploadAvatar(new MockMultipartFile("file","x.png","image/png",new byte[2*1024*1024+1])));
        assertThrows(BusinessException.class, () -> AvatarFiles.validatePath("https://foreign.test/x.png"));
        assertThrows(BusinessException.class, () -> AvatarFiles.validatePath("/upload/avatars/../secret.png"));
        AvatarFiles.validatePath("");
        byte[] webp = new ClassPathResource("avatar-test.webp").getContentAsByteArray();
        assertEquals("webp", AvatarFiles.suffix("avatar.webp", webp));
    }
    @Test void userPasswordRejectsWrongOldWeakUnchangedAndConcurrentUpdates() {
        var users = mock(UserMapper.class); var passwords = new Passwords();
        var service = new UserServiceImpl(users,mock(AddressMapper.class),mock(JwtTokenProvider.class),passwords,mock(SessionRegistry.class));
        var user = new User(); user.setId(8L); user.setPassword(passwords.encode("originalPassword8"));
        when(users.selectById(8L)).thenReturn(user);
        assertThrows(BusinessException.class, () -> service.updatePassword(8L,"wrong","newPassword8"));
        assertThrows(BusinessException.class, () -> service.updatePassword(8L,"originalPassword8","short"));
        assertThrows(BusinessException.class, () -> service.updatePassword(8L,"originalPassword8","originalPassword8"));
        assertThrows(BusinessException.class, () -> service.updatePassword(8L,"originalPassword8","新".repeat(25)));
        verify(users,never()).changePasswordIfUnchanged(anyLong(),anyString(),anyString());
        when(users.changePasswordIfUnchanged(anyLong(),anyString(),anyString())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.updatePassword(8L,"originalPassword8","newPassword8"));
        when(users.changePasswordIfUnchanged(anyLong(),anyString(),anyString())).thenReturn(1);
        service.updatePassword(8L,"originalPassword8","newPassword8");
        verify(users,times(2)).changePasswordIfUnchanged(eq(8L),eq(user.getPassword()),argThat(hash->passwords.matches("newPassword8",hash)));
    }
}
