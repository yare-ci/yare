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

package com.sabre.oss.yare.serializer.xml.mapper.converter.xml;

import com.sabre.oss.yare.common.converter.TypeConverter;
import com.sabre.oss.yare.core.model.Expression;
import com.sabre.oss.yare.serializer.model.*;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class ExpressionConverter {
    private final TypeConverter typeConverter;

    public ExpressionConverter(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    public PredicateSer map(Expression expression) {
        if (expression != null) {
            PredicateSer predicateSer = new PredicateSer();

            Object o = extractExpression(expression);
            if (o instanceof ValueSer) {
                predicateSer.withValue((ValueSer) o);
            } else if (o instanceof AndSer) {
                predicateSer.withAnd((AndSer) o);
            } else if (o instanceof OrSer) {
                predicateSer.withOr((OrSer) o);
            } else if (o instanceof NotSer) {
                predicateSer.withNot((NotSer) o);
            } else if (o instanceof OperatorSer) {
                predicateSer.withOperator((OperatorSer) o);
            } else if (o instanceof FunctionSer) {
                predicateSer.withFunction((FunctionSer) o);
            }

            return predicateSer;
        }

        return null;
    }


    public Object extractExpression(Expression expression) {
        Expression.Value value = expression.as(Expression.Value.class);
        if (value != null) {
            String type = typeConverter.toString(Type.class, value.getType());
            if (value.getValue() != null) {
                if (Collection.class.isAssignableFrom(value.getValue().getClass())) {
                    Collection<ValueSer> collect = ((Collection<?>) value.getValue()).stream()
                            .map(v -> typeConverter.toString(value.getType(), v))
                            .map(v -> new ValueSer().withValue(v))
                            .collect(Collectors.toList());
                    return new ValuesSer()
                            .withType(type)
                            .withValue(collect);
                }
                if (typeConverter.isApplicable(value.getType())) {
                    return new ValueSer()
                            .withType(type)
                            .withValue(typeConverter.toString(value.getType(), value.getValue()));
                }
            }
            return new CustomValueSer()
                    .withType(type)
                    .withAny(value.getValue());
        }

        Expression.Invocation invocation = expression.as(Expression.Invocation.class);
        if (invocation != null) {
            Expression.Operator operator = invocation.as(Expression.Operator.class);
            if (operator != null) {
                String call = operator.getCall();
                List<Object> arguments = operator.getArguments().stream().map(this::extractExpression).collect(Collectors.toList());
                switch (call) {
                    case "and":
                        return new AndSer()
                                .withBooleanExpression(arguments);
                    case "or":
                        return new OrSer()
                                .withBooleanExpression(arguments);
                    case "not":
                        return convertNot(arguments.get(0));
                    default:
                        return new OperatorSer()
                                .withType(call)
                                .withOperand(arguments);
                }
            }

            Expression.Function function = invocation.as(Expression.Function.class);
            if (function != null) {
                return new FunctionSer()
                        .withName(function.getCall())
                        .withReturnType(typeConverter.toString(Type.class, function.getType()))
                        .withParameter(extractParameter(function.getArguments()));
            }
        }

        Expression.Reference reference = expression.as(Expression.Reference.class);
        if (reference != null) {
            if (isEmpty(reference.getPath())) {
                return new ReferenceSer()
                        .withRef(reference.getReference());
            }
            return new FieldSer()
                    .withRef(reference.getReference())
                    .withPath(reference.getPath())
                    .withType(typeConverter.toString(Type.class, reference.getType()));
        }

        return null;
    }

    public Collection<ParameterSer> extractParameter(List<Expression> arguments) {
        return arguments.stream()
                .map(this::convertParameter)
                .collect(Collectors.toList());
    }

    private ParameterSer convertParameter(Expression expression) {
        ParameterSer parameterSer = new ParameterSer();

        parameterSer.withName(extractName(expression));

        Object o = extractExpression(expression);
        if (o instanceof ValueSer) {
            parameterSer.withValue((ValueSer) o);
        } else if (o instanceof ValuesSer) {
            parameterSer.withValues((ValuesSer) o);
        } else if (o instanceof CustomValueSer) {
            parameterSer.withCustomValue((CustomValueSer) o);
        } else if (o instanceof FieldSer) {
            parameterSer.withField((FieldSer) o);
        } else if (o instanceof ReferenceSer) {
            parameterSer.withReference((ReferenceSer) o);
        } else if (o instanceof FunctionSer) {
            parameterSer.withFunction((FunctionSer) o);
        }

        return parameterSer;
    }

    private String extractName(Expression expression) {
        Expression.Raw raw = expression.as(Expression.Raw.class);
        if (raw != null) {
            return raw.getName();
        }
        Expression.Value value = expression.as(Expression.Value.class);
        if (value != null) {
            return value.getName();
        }
        Expression.Reference reference = expression.as(Expression.Reference.class);
        if (reference != null) {
            return reference.getName();
        }
        Expression.Invocation invocation = expression.as(Expression.Invocation.class);
        if (invocation != null) {
            return invocation.getName();
        }
        return null;
    }

    private NotSer convertNot(Object o) {
        NotSer notSer = new NotSer();

        if (o instanceof OperatorSer) {
            notSer.withOperator((OperatorSer) o);
        } else if (o instanceof OrSer) {
            notSer.withOr((OrSer) o);
        } else if (o instanceof AndSer) {
            notSer.withAnd((AndSer) o);
        } else if (o instanceof NotSer) {
            notSer.withNot((NotSer) o);
        } else if (o instanceof FunctionSer) {
            notSer.withFunction((FunctionSer) o);
        } else if (o instanceof ValueSer) {
            notSer.withValue((ValueSer) o);
        }

        return notSer;
    }

}
