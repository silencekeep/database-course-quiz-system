package edu.ustb.service;

import edu.ustb.test.TestMyBatisUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class AuthServiceTest {
    static SqlSessionFactory factory;

    @BeforeAll
    static void setup() throws Exception { factory = TestMyBatisUtil.buildFactory(); }

    @Test
    void registerAndLoginFlow() throws Exception {
        AuthService auth = new AuthService(factory);
        String token = auth.register("acc1","User1","pwd");
        assertThat(token).isNotBlank();
        String token2 = auth.login("acc1","pwd");
        assertThat(token2).isNotBlank();
        Integer uid = auth.validateToken(token2);
        assertThat(uid).isNotNull();
    }

    @Test
    void duplicateAccountRejected() throws Exception {
        AuthService auth = new AuthService(factory);
        auth.register("acc2","User2","pwd");
        assertThatThrownBy(() -> auth.register("acc2","U","pwd")).hasMessageContaining("EXIST");
    }
}
