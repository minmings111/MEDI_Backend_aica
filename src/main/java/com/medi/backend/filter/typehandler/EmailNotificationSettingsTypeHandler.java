package com.medi.backend.filter.typehandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.backend.filter.dto.EmailNotificationSettings;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JSON 컬럼 → EmailNotificationSettings 변환 TypeHandler
 */
@MappedTypes(EmailNotificationSettings.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class EmailNotificationSettingsTypeHandler extends BaseTypeHandler<EmailNotificationSettings> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, EmailNotificationSettings parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = objectMapper.writeValueAsString(parameter);
            ps.setString(i, json);
        } catch (Exception e) {
            throw new SQLException("JSON 변환 실패: " + e.getMessage(), e);
        }
    }
    
    @Override
    public EmailNotificationSettings getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }
    
    @Override
    public EmailNotificationSettings getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }
    
    @Override
    public EmailNotificationSettings getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }
    
    private EmailNotificationSettings parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, EmailNotificationSettings.class);
        } catch (Exception e) {
            return null;
        }
    }
}

