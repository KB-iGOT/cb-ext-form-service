package com.karmayogi.form.repository;

import com.karmayogi.form.entity.FormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author anil
 */
@Repository
public interface FormSubmissionRepository extends JpaRepository<FormSubmission, String> {

    @Query(value = """
            SELECT formid, COUNT(*) as count
            FROM form_submissions
            WHERE formid IN :formIds
            GROUP BY formid
            """, nativeQuery = true)
    List<Object[]> countByFormIds(@Param("formIds") Set<String> formIds);

    @Query(value = """
            SELECT * FROM form_submissions
            WHERE formid = :formId
            AND submittedby = :userId
            AND contextid = :contextId
            ORDER BY submitteddate DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<FormSubmission> findLatestByFormIdAndUserIdAndContextId(
            @Param("formId") String formId,
            @Param("userId") String userId,
            @Param("contextId") String contextId);
}
