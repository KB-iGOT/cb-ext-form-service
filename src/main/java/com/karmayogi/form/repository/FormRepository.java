package com.karmayogi.form.repository;

import com.karmayogi.form.entity.Forms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * @author anil
 */
@Repository
public interface FormRepository extends JpaRepository<Forms, String> {}
