package edu.ustb.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import edu.ustb.mapper.SurveyMapper;
import edu.ustb.mapper.SurveyShareMapper;
import edu.ustb.model.SurveyShare;
import edu.ustb.model.Survey;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public class ShareService {
    private final SqlSessionFactory factory;

    public ShareService(SqlSessionFactory factory){ this.factory = factory; }

    public String createShare(Integer surveyId, Integer userId, boolean allowEdit) {
        try(SqlSession session = factory.openSession(true)) {
            SurveyMapper surveyMapper = session.getMapper(SurveyMapper.class);
            Survey s = surveyMapper.findById(surveyId);
            if(s==null) throw new RuntimeException("SURVEY_NOT_FOUND");
            if(!userId.equals(s.getSurveyOwnerId())) throw new RuntimeException("FORBIDDEN");
            SurveyShareMapper sm = session.getMapper(SurveyShareMapper.class);
            SurveyShare share = new SurveyShare();
            share.setShareId(newId());
            share.setShareSurveyId(surveyId);
            share.setShareUserId(userId);
            share.setShareAllowEdit(allowEdit?1:0);
            sm.insert(share);
            return buildShareLink(surveyId, share.getShareId());
        }
    }

    public String generateQrBase64(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 250,250);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildShareLink(Integer surveyId, Integer shareId){
        return "http://localhost:8080/api/surveys/"+surveyId+"?shareId="+shareId;
    }

    private int newId() { return (int)(System.nanoTime() & 0x7FFFFFFF); }
}