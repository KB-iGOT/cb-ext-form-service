package com.karmayogi.form.repository;

import com.karmayogi.form.entity.Form;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
/**
 * @author anil
 */
@Repository
public interface FormRepository
        extends JpaRepository<Form, String>,
                JpaSpecificationExecutor<Form>,
                FormRepositoryCustom  {
}
