package com.legaldocsgpt.templateservice.repository;
import com.legaldocsgpt.templateservice.model.Template;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends MongoRepository<Template, String> {}