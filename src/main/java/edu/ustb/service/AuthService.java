package edu.ustb.service;

import edu.ustb.mapper.UserMapper;
import edu.ustb.model.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private final SqlSessionFactory factory;
    private static class TokenInfo { int userId; long expireAt; }
    private final Map<String,TokenInfo> tokenStore = new ConcurrentHashMap<>();
    private final long ttlMillis = 30 * 60 * 1000L; // 30分钟

    public AuthService(SqlSessionFactory factory) { this.factory = factory; }

    public Integer validateToken(String token){
        if(token==null) return null; TokenInfo info = tokenStore.get(token); if(info==null) return null; if(System.currentTimeMillis()>info.expireAt){ tokenStore.remove(token); return null; }
        // 滑动过期
        info.expireAt = System.currentTimeMillis() + ttlMillis;
        return info.userId;
    }

    public void logout(String token){ if(token!=null) tokenStore.remove(token); }

    public String refresh(String oldToken){
        TokenInfo info = tokenStore.get(oldToken); if(info==null) throw new RuntimeException("INVALID_TOKEN"); if(System.currentTimeMillis()>info.expireAt){ tokenStore.remove(oldToken); throw new RuntimeException("TOKEN_EXPIRED"); }
        tokenStore.remove(oldToken);
        return issueToken(info.userId);
    }

    public User getUserById(Integer userId){
        if(userId==null) return null;
        try(SqlSession session = factory.openSession()){
            UserMapper um = session.getMapper(UserMapper.class);
            return um.findById(userId);
        }
    }

    public String register(String account, String name, String rawPassword){
        try(SqlSession session = factory.openSession(true)) {
            UserMapper um = session.getMapper(UserMapper.class);
            if(um.findByAccount(account) != null) throw new RuntimeException("ACCOUNT_EXISTS");
            User u = new User();
            u.setUserId(newId());
            u.setUserAccount(account);
            u.setUserName(name);
            u.setUserPassword(hash(rawPassword));
            u.setUserRegisterTime(LocalDate.now());
            u.setUserLastLoginTime(LocalDate.now());
            um.insert(u);
            String token = issueToken(u.getUserId());
            return token;
        }
    }

    public String login(String account, String rawPassword){
        try(SqlSession session = factory.openSession(true)) {
            UserMapper um = session.getMapper(UserMapper.class);
            User u = um.findByAccount(account);
            if(u==null) throw new RuntimeException("NOT_FOUND");
            if(!u.getUserPassword().equals(hash(rawPassword))) throw new RuntimeException("BAD_CREDENTIALS");
            um.updateLastLogin(u.getUserId(), LocalDate.now());
            return issueToken(u.getUserId());
        }
    }

    private String issueToken(Integer userId){
        String token = UUID.randomUUID().toString().replace("-","");
        TokenInfo info = new TokenInfo(); info.userId = userId; info.expireAt = System.currentTimeMillis() + ttlMillis; tokenStore.put(token, info); return token; }

    private String hash(String s){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(s.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private int newId() { return (int)(System.nanoTime() & 0x7FFFFFFF); }
}