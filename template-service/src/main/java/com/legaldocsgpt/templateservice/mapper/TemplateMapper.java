package com.legaldocsgpt.templateservice.mapper;

import com.legaldocsgpt.shared.dto.TemplateDefinition;
import com.legaldocsgpt.templateservice.model.Template;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TemplateMapper {

    TemplateDefinition toDto(Template template);

    List<TemplateDefinition> toDtoList(List<Template> templates);

    TemplateDefinition.TemplateField toFieldDto(Template.TemplateField field);
}