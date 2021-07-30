/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.compiler.linter.impl;

import io.ballerina.compiler.linter.impl.rules.ParenthesesUsageRule;
import io.ballerina.projects.plugins.CodeAnalysisContext;
import io.ballerina.projects.plugins.CodeAnalyzer;
import org.ballerinalang.compiler.BLangCompilerException;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * The place where linter rule registers.
 *
 * @since 2.0.0
 */
class LinterCodeAnalyzer extends CodeAnalyzer {

    private static final List<Class<? extends LinterRule>> RULES = List.of(ParenthesesUsageRule.class);

    @Override
    public void init(CodeAnalysisContext analysisContext) {

        registerLinterRules(analysisContext);
    }

    private void registerLinterRules(CodeAnalysisContext ctx) {

        for (Class<? extends LinterRule> ruleClass : RULES) {
            try {
                final LinterRule linterRule = ruleClass.getDeclaredConstructor().newInstance();
                ctx.addSyntaxNodeAnalysisTask(linterRule, linterRule.getSyntaxKinds());
            } catch (NoSuchMethodException | InstantiationException |
                    IllegalAccessException | InvocationTargetException e) {
                throw new BLangCompilerException("Failed to load linter plugin :" + ruleClass.getCanonicalName(), e);
            }
        }
    }
}
