package edu.ustb.service;

import edu.ustb.mapper.SurveyMapper;
import edu.ustb.mapper.SurveyShareMapper;
import edu.ustb.mapper.UserMapper;
import edu.ustb.model.Survey;
import edu.ustb.model.SurveyShare;
import edu.ustb.model.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.*;

public class SurveyQueryService {
    private final SqlSessionFactory factory;
    public SurveyQueryService(SqlSessionFactory factory){ this.factory = factory; }

    public List<Survey> listMySurveys(Integer userId){
        try(SqlSession s = factory.openSession()){
            return s.getMapper(SurveyMapper.class).findByOwner(userId);
        }
    }

    public Map<String,Object> pageMySurveys(Integer userId, int page, int pageSize){
        int offset = (page-1)*pageSize; if(offset<0) offset=0; try(SqlSession s = factory.openSession()){
            SurveyMapper sm = s.getMapper(SurveyMapper.class);
            int total = sm.countByOwner(userId);
            List<Survey> items = total==0? java.util.Collections.emptyList(): sm.pageByOwner(userId, pageSize, offset);
            Map<String,Object> res = new LinkedHashMap<>(); res.put("page",page); res.put("page_size",pageSize); res.put("total",total); res.put("items",items); return res; }
    }

    public List<Survey> listSharedToMe(Integer userId){
        try(SqlSession s = factory.openSession()){
            return s.getMapper(SurveyMapper.class).findSharedToUser(userId);
        }
    }

    public Map<String,Object> pageSharedToMe(Integer userId, int page, int pageSize){
        int offset = (page-1)*pageSize; if(offset<0) offset=0; try(SqlSession s = factory.openSession()){
            SurveyMapper sm = s.getMapper(SurveyMapper.class);
            int total = sm.countSharedToUser(userId);
            List<Survey> items = total==0? java.util.Collections.emptyList(): sm.pageSharedToUser(userId, pageSize, offset);
            Map<String,Object> res = new LinkedHashMap<>(); res.put("page",page); res.put("page_size",pageSize); res.put("total",total); res.put("items",items); return res; }
    }

    public Map<String,Object> pageSurveyShares(Integer surveyId, Integer requesterId, int page, int pageSize){
        int offset=(page-1)*pageSize; if(offset<0) offset=0; try(SqlSession s = factory.openSession()){
            SurveyMapper sm = s.getMapper(SurveyMapper.class); Survey survey = sm.findById(surveyId); if(survey==null) return Map.of("page",page,"page_size",pageSize,"total",0,"items", java.util.Collections.emptyList()); if(!Objects.equals(survey.getSurveyOwnerId(), requesterId)) throw new RuntimeException("FORBIDDEN");
            SurveyShareMapper shm = s.getMapper(SurveyShareMapper.class); UserMapper um = s.getMapper(UserMapper.class);
            int total = shm.countBySurvey(surveyId); List<SurveyShare> raw = total==0? java.util.Collections.emptyList(): shm.pageBySurvey(surveyId, pageSize, offset);
            List<Map<String,Object>> items = new ArrayList<>(); for(SurveyShare sh: raw){ Map<String,Object> row=new LinkedHashMap<>(); row.put("share_id",sh.getShareId()); row.put("user_id",sh.getShareUserId()); row.put("allow_edit",sh.getShareAllowEdit()); User u=um.findById(sh.getShareUserId()); if(u!=null){ row.put("user_account",u.getUserAccount()); row.put("user_name",u.getUserName()); } items.add(row);} Map<String,Object> res=new LinkedHashMap<>(); res.put("page",page); res.put("page_size",pageSize); res.put("total",total); res.put("items",items); return res; }
    }
}
