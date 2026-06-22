package com.karmayogi.form.repository;

import com.karmayogi.form.entity.PublicFormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


/**
 * @author anil
 */
@Repository
public interface PublicFormSubmissionRepository extends JpaRepository<PublicFormSubmission, String> {

    @Query(value = """
            SELECT * FROM public_form_submissions
            WHERE formid = :formId
            AND contextid = :contextId
            ORDER BY submitteddate DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<PublicFormSubmission> findLatestByFormIdAndContextId(
            @Param("formId") String formId,
            @Param("contextId") String contextId);

}
