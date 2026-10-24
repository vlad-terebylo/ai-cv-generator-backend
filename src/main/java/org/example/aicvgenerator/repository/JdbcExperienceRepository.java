package org.example.aicvgenerator.repository;

import java.util.List;

import org.example.aicvgenerator.repository.entity.ExperienceEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Profile("jdbc")
public class JdbcExperienceRepository implements ExperienceRepository {

    private static final RowMapper<ExperienceEntity> ROW_MAPPER = (rs, rowNum) -> ExperienceEntity.builder()
            .id(rs.getLong("id"))
            .cvId(rs.getLong("cv_id"))
            .startYear(rs.getObject("start_year", Integer.class))
            .endYear(rs.getObject("end_year", Integer.class))
            .position(rs.getString("position"))
            .company(rs.getString("company"))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<ExperienceEntity> findByCvId(long cvId) {
        return jdbc.query("SELECT * FROM experience WHERE cv_id = :cvId ORDER BY start_year DESC NULLS LAST, id",
                new MapSqlParameterSource("cvId", cvId), ROW_MAPPER);
    }

    @Override
    public void insertAll(List<ExperienceEntity> entities) {
        if (entities.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = entities.stream()
                .map(BeanPropertySqlParameterSource::new)
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO experience (cv_id, start_year, end_year, position, company)
                VALUES (:cvId, :startYear, :endYear, :position, :company)
                """, batch);
    }

    @Override
    public void deleteByCvId(long cvId) {
        jdbc.update("DELETE FROM experience WHERE cv_id = :cvId", new MapSqlParameterSource("cvId", cvId));
    }
}
