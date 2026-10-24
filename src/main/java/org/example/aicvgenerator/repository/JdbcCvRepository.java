package org.example.aicvgenerator.repository;

import java.util.Optional;

import org.example.aicvgenerator.repository.entity.CvEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Profile("jdbc")
public class JdbcCvRepository implements CvRepository {

    private static final RowMapper<CvEntity> ROW_MAPPER = (rs, rowNum) -> CvEntity.builder()
            .id(rs.getLong("id"))
            .firstName(rs.getString("first_name"))
            .lastName(rs.getString("last_name"))
            .email(rs.getString("email"))
            .summary(rs.getString("summary"))
            .sourceFileName(rs.getString("source_file_name"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Optional<CvEntity> findById(long id) {
        return jdbc.query("SELECT * FROM cv WHERE id = :id", new MapSqlParameterSource("id", id), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public Optional<CvEntity> findByEmail(String email) {
        return jdbc.query("SELECT * FROM cv WHERE email = :email", new MapSqlParameterSource("email", email), ROW_MAPPER)
                .stream().findFirst();
    }

    /** Inserts the row and returns its generated id. */
    @Override
    public long insert(CvEntity entity) {
        var keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO cv (first_name, last_name, email, summary, source_file_name)
                VALUES (:firstName, :lastName, :email, :summary, :sourceFileName)
                """, new BeanPropertySqlParameterSource(entity), keys, new String[] {"id"});
        return keys.getKey().longValue();
    }

    @Override
    public void update(CvEntity entity) {
        jdbc.update("""
                UPDATE cv
                SET first_name = :firstName, last_name = :lastName, email = :email,
                    summary = :summary, source_file_name = :sourceFileName, updated_at = now()
                WHERE id = :id
                """, new BeanPropertySqlParameterSource(entity));
    }
}
