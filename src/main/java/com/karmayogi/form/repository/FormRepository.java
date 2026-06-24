package com.karmayogi.form.repository;

import com.karmayogi.form.entity.Forms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author anil
 */
@Repository
public interface FormRepository extends JpaRepository<Forms, String> {

    @Query(value = """
            SELECT * FROM forms
            WHERE contexttype = :contextType
            AND orgid = :orgId
            AND additionalproperties->>'identifier' = :identifier
            AND status IN :statuses
            LIMIT 10
            """, nativeQuery = true)
    List<Forms> findExistingPeerSurveys(
            @Param("contextType") String contextType,
            @Param("orgId") String orgId,
            @Param("identifier") String identifier,
            @Param("statuses") List<String> statuses);

}
