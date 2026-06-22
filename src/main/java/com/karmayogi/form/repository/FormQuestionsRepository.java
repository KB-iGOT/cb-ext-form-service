package com.karmayogi.form.repository;

import com.karmayogi.form.entity.FormQuestions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author anil
 */
@Repository
public interface FormQuestionsRepository extends JpaRepository<FormQuestions, String> {

    List<FormQuestions> findByFormIdOrderByQuestionOrderAsc(String formId);
}
