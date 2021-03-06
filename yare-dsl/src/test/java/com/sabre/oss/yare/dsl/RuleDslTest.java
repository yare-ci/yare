/*
 * MIT License
 *
 * Copyright 2018 Sabre GLBL Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sabre.oss.yare.dsl;

import com.sabre.oss.yare.common.converter.DefaultTypeConverters;
import com.sabre.oss.yare.common.converter.TypeConverter;
import com.sabre.oss.yare.core.model.Attribute;
import com.sabre.oss.yare.core.model.Expression;
import com.sabre.oss.yare.core.model.Fact;
import com.sabre.oss.yare.core.model.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static com.sabre.oss.yare.core.model.ExpressionFactory.*;
import static com.sabre.oss.yare.dsl.RuleDsl.*;
import static org.assertj.core.api.Assertions.assertThat;

class RuleDslTest {
    private TypeConverter converter = DefaultTypeConverters.getDefaultTypeConverter();

    @Test
    void shouldProperlyBuildRule() {
        Rule rule = ruleBuilder()
                .name("this.is.MyRuleName")
                .fact("exampleFact", ExampleFact.class)
                .fact("otherFact", OtherFact.class)
                .attribute("stringValue", "string")
                .attribute("doubleValue", 1.0)
                .predicate(
                        and(
                                or(
                                        lessOrEqual(
                                                value(123L),
                                                field("otherFact.number", Long.class)
                                        ),
                                        match(
                                                reference("stringValue", String.class),
                                                value("10")
                                        ),
                                        operator("asd",
                                                field("otherFact", "enabled", String.class),
                                                value(true)
                                        ),
                                        and(
                                                equal(field("otherFact.enabled", Boolean.class),
                                                        value(true)
                                                ),
                                                not(
                                                        value("true", Boolean.class)
                                                )
                                        )
                                ),
                                less(
                                        field("exampleFact.startDate"),
                                        field("exampleFact.stopDate")
                                ),
                                operator("contains", values(String.class, value("a"), value("b"), value("c")), value("c")),
                                function("function", Boolean.class,
                                        param("param1", reference("ruleName")),
                                        param("param2", value("my value"))
                                )
                        )
                )
                .action("exampleAction", param("param1", reference("ctx")))
                .build();

        assertThat(rule.getAttributes()).containsExactly(
                new Attribute("ruleName", String.class, "this.is.MyRuleName"),
                new Attribute("stringValue", String.class, "string"),
                new Attribute("doubleValue", Double.class, 1.0)
        );
        assertThat(rule.getFacts()).containsExactly(
                new Fact("exampleFact", ExampleFact.class),
                new Fact("otherFact", OtherFact.class)
        );
        assertThat(rule.getPredicate()).isEqualTo(expectedValidPredicateModel());
        assertThat(rule.getActions()).containsExactly(
                actionOf("exampleAction", "exampleAction",
                        referenceOf("param1", Object.class, "ctx")
                )
        );
    }

    @ParameterizedTest
    @MethodSource("testValue")
    void shouldProperlyCreateValue(Class<Object> type, Object object) {
        // when
        ExpressionOperand<Object> operand = value(object, type);

        // then
        Expression expression = operand.getExpression(null, null);
        assertThat(expression).isInstanceOf(Expression.Value.class);

        assertThat(((Expression.Value) expression).getValue()).isEqualTo(object);
        assertThat(expression.getType()).isEqualTo(type);
    }

    private static Stream<Arguments> testValue() {
        return Stream.of(
                Arguments.of(Boolean.class, true),
                Arguments.of(Boolean.class, null),
                Arguments.of(Boolean.class, false),
                Arguments.of(Integer.class, 10),
                Arguments.of(Long.class, 100L),
                Arguments.of(String.class, "string"),
                Arguments.of(BigDecimal.class, new BigDecimal("100.123")),
                Arguments.of(ZonedDateTime.class, ZonedDateTime.now()),
                Arguments.of(Bean.class, new Bean()),
                Arguments.of(Bean.class, null),
                Arguments.of(List.class, new ArrayList<>())
        );
    }

    @ParameterizedTest
    @MethodSource("testValues")
    void assertBuildInTypeCollectionValues(Class<Object> type, Object[] objects) {
        // when
        CollectionOperand<Object> operand = values(type, objects);

        // then
        Expression expression = operand.getExpression(null, null);
        assertThat(expression).isInstanceOf(Expression.Value.class);

        assertThat(((Expression.Value) expression).getValue()).isEqualTo(Arrays.asList(objects));

        String parametrizedType = String.format("java.util.List<%s>", converter.toString(Type.class, type));
        Type fullType = DefaultTypeConverters.getDefaultTypeConverter().fromString(Type.class, parametrizedType);
        assertThat(expression.getType()).isEqualTo(fullType);
    }

    private static Stream<Arguments> testValues() {
        return Stream.of(
                Arguments.of(Boolean.class, new Boolean[]{true, false, null}),
                Arguments.of(Integer.class, new Integer[]{10, 20}),
                Arguments.of(Long.class, new Long[]{10L, 20L}),
                Arguments.of(Integer.class, new Integer[]{10}),
                Arguments.of(String.class, new String[]{"1", "2"}),
                Arguments.of(Bean.class, new Bean[]{new Bean(), new Bean(), null})
        );
    }

    private Expression.Operator expectedValidPredicateModel() {
        return operatorOf(null, Boolean.class, "and",
                operatorOf(null, Boolean.class, "or",
                        operatorOf(null, Boolean.class, "less-or-equal",
                                valueOf(null, Long.class, 123L),
                                referenceOf(null, OtherFact.class, "otherFact", Long.class, "number")
                        ),
                        operatorOf(null, Boolean.class, "match",
                                referenceOf(null, String.class, "stringValue"),
                                valueOf(null, String.class, "10")
                        ),
                        operatorOf(null, Boolean.class, "asd",
                                referenceOf(null, OtherFact.class, "otherFact", String.class, "enabled"),
                                valueOf(null, Boolean.class, true)
                        ),
                        operatorOf(null, Boolean.class, "and",
                                operatorOf(null, Boolean.class, "equal",
                                        referenceOf(null, OtherFact.class, "otherFact", Boolean.class, "enabled"),
                                        valueOf(null, Boolean.class, true)
                                ),
                                operatorOf(null, Boolean.class, "not",
                                        valueOf(null, Boolean.class, true)
                                )
                        )
                ),
                operatorOf(null, Boolean.class, "less",
                        referenceOf(null, ExampleFact.class, "exampleFact", Object.class, "startDate"),
                        referenceOf(null, ExampleFact.class, "exampleFact", Object.class, "stopDate")
                ),
                operatorOf(null, Boolean.class, "contains",
                        valueOf(null, converter.fromString(Type.class, "java.util.List<java.lang.String>"), Arrays.asList("a", "b", "c")),
                        valueOf(null, String.class, "c")
                ),
                functionOf("function", Boolean.class, "function",
                        referenceOf("param1", Object.class, "ruleName"),
                        valueOf("param2", String.class, "my value")
                )
        );
    }

    private static class Bean {
    }

    private static class ExampleFact {
        public Date startDate;
        public Date stopDate;
    }

    public static class OtherFact {
        public boolean enabled;
        public Long number;
    }
}
