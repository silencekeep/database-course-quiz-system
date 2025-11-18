package edu.ustb.model;

import java.time.LocalDate;

public class User {
    private Integer userId;
    private String userAccount;
    private String userName;
    private String userPassword;
    private LocalDate userRegisterTime;
    private LocalDate userLastLoginTime;

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getUserAccount() { return userAccount; }
    public void setUserAccount(String userAccount) { this.userAccount = userAccount; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserPassword() { return userPassword; }
    public void setUserPassword(String userPassword) { this.userPassword = userPassword; }
    public LocalDate getUserRegisterTime() { return userRegisterTime; }
    public void setUserRegisterTime(LocalDate userRegisterTime) { this.userRegisterTime = userRegisterTime; }
    public LocalDate getUserLastLoginTime() { return userLastLoginTime; }
    public void setUserLastLoginTime(LocalDate userLastLoginTime) { this.userLastLoginTime = userLastLoginTime; }
}