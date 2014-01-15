/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.api.rule;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.FieldUtils2;
import org.sonar.api.utils.SonarException;
import org.sonar.check.Cardinality;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Read definitions of rules based on the annotations provided by sonar-check-api.
 * </p>
 * It is internally used by {@link org.sonar.api.rule.RuleDefinitions} and can't be directly
 * used by plugins.
 * @since 4.2
 */
class AnnotationRuleDefinitions {

  private static final Logger LOG = LoggerFactory.getLogger(AnnotationRuleDefinitions.class);

  void loadRules(RuleDefinitions.NewRepository repo, Class... annotatedClasses) {
    for (Class annotatedClass : annotatedClasses) {
      loadRule(repo, annotatedClass);
    }
  }

  private void loadRule(RuleDefinitions.NewRepository repo, Class clazz) {
    org.sonar.check.Rule ruleAnnotation = AnnotationUtils.getAnnotation(clazz, org.sonar.check.Rule.class);
    if (ruleAnnotation != null) {
      loadRule(repo, clazz, ruleAnnotation);
    } else {
      LOG.warn("The class " + clazz.getCanonicalName() + " should be annotated with " + org.sonar.check.Rule.class);
    }
  }

  private void loadRule(RuleDefinitions.NewRepository repo, Class clazz, org.sonar.check.Rule ruleAnnotation) {
    String ruleKey = StringUtils.defaultIfEmpty(ruleAnnotation.key(), clazz.getCanonicalName());
    String ruleName = StringUtils.defaultIfEmpty(ruleAnnotation.name(), null);
    String description = StringUtils.defaultIfEmpty(ruleAnnotation.description(), null);

    RuleDefinitions.NewRule rule = repo.newRule(ruleKey);
    rule.setName(ruleName).setHtmlDescription(description);
    rule.setDefaultSeverity(ruleAnnotation.priority().name());
    rule.setTemplate(ruleAnnotation.cardinality() == Cardinality.MULTIPLE);
    rule.setStatus(RuleDefinitions.Status.valueOf(ruleAnnotation.status()));

    List<Field> fields = FieldUtils2.getFields(clazz, true);
    for (Field field : fields) {
      loadParameters(rule, field);
    }
  }

  private void loadParameters(RuleDefinitions.NewRule rule, Field field) {
    org.sonar.check.RuleProperty propertyAnnotation = field.getAnnotation(org.sonar.check.RuleProperty.class);
    if (propertyAnnotation != null) {
      String fieldKey = StringUtils.defaultIfEmpty(propertyAnnotation.key(), field.getName());
      RuleDefinitions.NewParam param = rule.newParam(fieldKey)
        .setDescription(propertyAnnotation.description())
        .setDefaultValue(propertyAnnotation.defaultValue());

      if (!StringUtils.isBlank(propertyAnnotation.type())) {
        try {
          param.setType(RuleParamType.parse(propertyAnnotation.type().trim()));
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Invalid property type [" + propertyAnnotation.type() + "]", e);
        }
      } else {
        param.setType(guessType(field.getType()));
      }
    }
  }

  private static final Function<Class<?>, RuleParamType> TYPE_FOR_CLASS = Functions.forMap(
    ImmutableMap.<Class<?>, RuleParamType>builder()
      .put(Integer.class, RuleParamType.INTEGER)
      .put(int.class, RuleParamType.INTEGER)
      .put(Float.class, RuleParamType.FLOAT)
      .put(float.class, RuleParamType.FLOAT)
      .put(Boolean.class, RuleParamType.BOOLEAN)
      .put(boolean.class, RuleParamType.BOOLEAN)
      .build(),
    RuleParamType.STRING);

  @VisibleForTesting
  static RuleParamType guessType(Class<?> type) {
    return TYPE_FOR_CLASS.apply(type);
  }
}
