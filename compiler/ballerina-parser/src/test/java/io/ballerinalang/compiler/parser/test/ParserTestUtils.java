/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerinalang.compiler.parser.test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ballerinalang.compiler.internal.parser.BallerinaParser;
import io.ballerinalang.compiler.internal.parser.ParserFactory;
import io.ballerinalang.compiler.internal.parser.ParserRuleContext;
import io.ballerinalang.compiler.internal.parser.tree.STBasicLiteralNode;
import io.ballerinalang.compiler.internal.parser.tree.STBuiltinSimpleNameReferenceNode;
import io.ballerinalang.compiler.internal.parser.tree.STDocumentationLineToken;
import io.ballerinalang.compiler.internal.parser.tree.STIdentifierToken;
import io.ballerinalang.compiler.internal.parser.tree.STLiteralValueToken;
import io.ballerinalang.compiler.internal.parser.tree.STMissingToken;
import io.ballerinalang.compiler.internal.parser.tree.STNode;
import io.ballerinalang.compiler.internal.parser.tree.STSimpleNameReferenceNode;
import io.ballerinalang.compiler.internal.parser.tree.STTemplateExpressionNode;
import io.ballerinalang.compiler.internal.parser.tree.STToken;
import io.ballerinalang.compiler.internal.parser.tree.STXMLTextNode;
import io.ballerinalang.compiler.internal.parser.tree.SyntaxTrivia;
import io.ballerinalang.compiler.syntax.tree.SyntaxKind;
import io.ballerinalang.compiler.syntax.tree.SyntaxTree;
import io.ballerinalang.compiler.text.TextDocument;
import io.ballerinalang.compiler.text.TextDocuments;
import org.testng.Assert;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.ballerinalang.compiler.internal.parser.tree.SyntaxUtils.isSTNodePresent;
import static io.ballerinalang.compiler.parser.test.ParserTestConstants.CHILDREN_FIELD;
import static io.ballerinalang.compiler.parser.test.ParserTestConstants.IS_MISSING_FIELD;
import static io.ballerinalang.compiler.parser.test.ParserTestConstants.KIND_FIELD;
import static io.ballerinalang.compiler.parser.test.ParserTestConstants.LEADING_TRIVIA;
import static io.ballerinalang.compiler.parser.test.ParserTestConstants.TRAILING_TRIVIA;
import static io.ballerinalang.compiler.parser.test.ParserTestConstants.VALUE_FIELD;

/**
 * Convenient methods for testing the parser.
 *
 * @since 1.2.0
 */
public class ParserTestUtils {

    private static final Path RESOURCE_DIRECTORY = Paths.get("src/test/resources/");

    /**
     * Test parsing a valid source.
     *
     * @param sourceFilePath Path to the ballerina file
     * @param context Context to start parsing the given source
     * @param assertFilePath File to assert the resulting tree after parsing
     */
    public static void test(Path sourceFilePath, ParserRuleContext context, Path assertFilePath) {
        String content = getSourceText(sourceFilePath);
        test(content, context, assertFilePath);
    }

    /**
     * Test parsing a valid source.
     *
     * @param source Input source that represent a ballerina code
     * @param context Context to start parsing the given source
     * @param assertFilePath File to assert the resulting tree after parsing
     */
    public static void test(String source, ParserRuleContext context, Path assertFilePath) {
        // Parse the source
        BallerinaParser parser = ParserFactory.getParser(source);
        STNode syntaxTree = parser.parse(context);

        // Read the assertion file
        JsonObject assertJson = readAssertFile(RESOURCE_DIRECTORY.resolve(assertFilePath));

        // Validate the tree against the assertion file
        assertNode(syntaxTree, assertJson);
    }

    /**
     * Returns Ballerina source code in the given file as a {@code String}.
     *
     * @param sourceFilePath Path to the ballerina file
     * @return source code as a {@code String}
     */
    public static String getSourceText(Path sourceFilePath) {
        try {
            return new String(Files.readAllBytes(RESOURCE_DIRECTORY.resolve(sourceFilePath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a {@code SyntaxTree} after parsing the give source code path.
     *
     * @param sourceFilePath Path to the ballerina file
     */
    public static SyntaxTree parseFile(Path sourceFilePath) {
        String text = getSourceText(sourceFilePath);
        TextDocument textDocument = TextDocuments.from(text);
        return SyntaxTree.from(textDocument, sourceFilePath.getFileName().toString());
    }

    private static JsonObject readAssertFile(Path filePath) {
        Gson gson = new Gson();
        try {
            return gson.fromJson(new FileReader(filePath.toFile()), JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertNode(STNode node, JsonObject json) {
        // Remove the wrappers
        if (node instanceof STBasicLiteralNode) {
            node = ((STBasicLiteralNode) node).literalToken;
        } else if (node instanceof STSimpleNameReferenceNode) {
            node = ((STSimpleNameReferenceNode) node).name;
        } else if (node instanceof STBuiltinSimpleNameReferenceNode) {
            node = ((STBuiltinSimpleNameReferenceNode) node).name;
        } else if (node instanceof STXMLTextNode) {
            node = ((STXMLTextNode) node).content;
        }

        aseertNodeKind(json, node);

        if (isMissingToken(json)) {
            Assert.assertTrue(node instanceof STMissingToken,
                    "'" + node.toString().trim() + "' expected to be a STMissingToken, but found '" + node.kind + "'.");
            return;
        }

        // If the expected token is not a missing node, then validate it's content
        Assert.assertFalse(node instanceof STMissingToken, "Expected:" + json + ", but found: " + node);
        if (isTerminalNode(node.kind)) {
            assertTerminalNode(json, node);
        } else {
            assertNonTerminalNode(json, CHILDREN_FIELD, node);
        }
    }

    private static boolean isMissingToken(JsonObject json) {
        JsonElement isMissing = json.get(IS_MISSING_FIELD);
        return isMissing != null && isMissing.getAsBoolean();
    }

    private static void aseertNodeKind(JsonObject json, STNode node) {
        SyntaxKind expectedNodeKind = getNodeKind(json.get(KIND_FIELD).getAsString());
        SyntaxKind actualNodeKind = node.kind;
        Assert.assertEquals(actualNodeKind, expectedNodeKind, "error at node [" + node.toString() + "].");
    }

    private static void assertTerminalNode(JsonObject json, STNode node) {
        // If this is a terminal node, it has to be a STToken (i.e: lexeme)
        if (isTrivia(node.kind)) {
            Assert.assertTrue(node instanceof SyntaxTrivia);
        } else {
            Assert.assertTrue(node instanceof STToken);
        }

        // Validate the token text, if this is not a syntax token.
        // e.g: identifiers, basic-literals, etc.
        if (!isSyntaxToken(node.kind)) {
            String expectedText;
            if (node.kind == SyntaxKind.END_OF_LINE_TRIVIA) {
                expectedText = System.lineSeparator();
            } else {
                expectedText = json.get(VALUE_FIELD).getAsString();
            }
            String actualText = getTokenText(node);
            Assert.assertEquals(actualText, expectedText);
        }

        if (!ParserTestUtils.isTrivia(node.kind)) {
            validateTrivia(json, (STToken) node);
        }
    }

    private static void validateTrivia(JsonObject json, STToken token) {
        if (json.has(LEADING_TRIVIA)) {
            assertNonTerminalNode(json, LEADING_TRIVIA, token.leadingTrivia);
        }

        if (json.has(TRAILING_TRIVIA)) {
            assertNonTerminalNode(json, TRAILING_TRIVIA, token.trailingTrivia);
        }
    }

    private static void assertNonTerminalNode(JsonObject json, String keyInJson, STNode tree) {
        JsonArray children = json.getAsJsonArray(keyInJson);
        int size = children.size();
        int j = 0;

        Assert.assertEquals(getNonEmptyChildCount(tree), size, "mismatching child count for '" + tree.toString() + "'");

        for (int i = 0; i < size; i++) {
            // Skip the optional fields that are not present and get the next
            // available node.
            STNode nextChild = tree.childInBucket(j++);
            while (!isSTNodePresent(nextChild)) {
                nextChild = tree.childInBucket(j++);
            }

            // Assert the actual child node against the expected child node.
            assertNode(nextChild, (JsonObject) children.get(i));
        }
    }

    private static int getNonEmptyChildCount(STNode tree) {
        int count = 0;
        for (int i = 0; i < tree.bucketCount(); i++) {
            STNode nextChild = tree.childInBucket(i);
            if (isSTNodePresent(nextChild)) {
                count++;
            }
        }

        return count;
    }

    public static boolean isTerminalNode(SyntaxKind syntaxKind) {
        return SyntaxKind.IMPORT_DECLARATION.compareTo(syntaxKind) > 0 || syntaxKind == SyntaxKind.EOF_TOKEN ||
                syntaxKind == SyntaxKind.XML_TEXT;
    }

    public static boolean isSyntaxToken(SyntaxKind syntaxKind) {
        return SyntaxKind.IDENTIFIER_TOKEN.compareTo(syntaxKind) > 0 || syntaxKind == SyntaxKind.EOF_TOKEN;
    }

    public static boolean isTrivia(SyntaxKind syntaxKind) {
        switch (syntaxKind) {
            case WHITESPACE_TRIVIA:
            case END_OF_LINE_TRIVIA:
            case COMMENT:
            case INVALID:
                return true;
            default:
                return false;
        }
    }

    public static String getTokenText(STNode token) {
        switch (token.kind) {
            case IDENTIFIER_TOKEN:
                return ((STIdentifierToken) token).text;
            case STRING_LITERAL:
                String val = ((STLiteralValueToken) token).text;
                int stringLen = val.length();
                int lastCharPosition = val.endsWith("\"") ? stringLen - 1 : stringLen;
                return val.substring(1, lastCharPosition);
            case DECIMAL_INTEGER_LITERAL:
            case HEX_INTEGER_LITERAL:
            case DECIMAL_FLOATING_POINT_LITERAL:
            case HEX_FLOATING_POINT_LITERAL:
                return ((STLiteralValueToken) token).text;
            case WHITESPACE_TRIVIA:
            case END_OF_LINE_TRIVIA:
            case COMMENT:
            case INVALID:
                return ((SyntaxTrivia) token).text;
            case DOCUMENTATION_LINE:
                return ((STDocumentationLineToken) token).text;
            case XML_TEXT:
            case XML_TEXT_CONTENT:
            case TEMPLATE_STRING:
                return ((STLiteralValueToken) token).text;
            default:
                return token.kind.toString();

        }
    }

    private static SyntaxKind getNodeKind(String kind) {
        switch (kind) {
            case "MODULE_PART":
                return SyntaxKind.MODULE_PART;
            case "TYPE_DEFINITION":
                return SyntaxKind.TYPE_DEFINITION;
            case "FUNCTION_DEFINITION":
                return SyntaxKind.FUNCTION_DEFINITION;
            case "IMPORT_DECLARATION":
                return SyntaxKind.IMPORT_DECLARATION;
            case "SERVICE_DECLARATION":
                return SyntaxKind.SERVICE_DECLARATION;
            case "LISTENER_DECLARATION":
                return SyntaxKind.LISTENER_DECLARATION;
            case "CONST_DECLARATION":
                return SyntaxKind.CONST_DECLARATION;
            case "MODULE_VAR_DECL":
                return SyntaxKind.MODULE_VAR_DECL;
            case "XML_NAMESPACE_DECLARATION":
                return SyntaxKind.XML_NAMESPACE_DECLARATION;
            case "ANNOTATION_DECLARATION":
                return SyntaxKind.ANNOTATION_DECLARATION;

            // Keywords
            case "PUBLIC_KEYWORD":
                return SyntaxKind.PUBLIC_KEYWORD;
            case "PRIVATE_KEYWORD":
                return SyntaxKind.PRIVATE_KEYWORD;
            case "FUNCTION_KEYWORD":
                return SyntaxKind.FUNCTION_KEYWORD;
            case "TYPE_KEYWORD":
                return SyntaxKind.TYPE_KEYWORD;
            case "EXTERNAL_KEYWORD":
                return SyntaxKind.EXTERNAL_KEYWORD;
            case "RETURNS_KEYWORD":
                return SyntaxKind.RETURNS_KEYWORD;
            case "RECORD_KEYWORD":
                return SyntaxKind.RECORD_KEYWORD;
            case "OBJECT_KEYWORD":
                return SyntaxKind.OBJECT_KEYWORD;
            case "REMOTE_KEYWORD":
                return SyntaxKind.REMOTE_KEYWORD;
            case "CLIENT_KEYWORD":
                return SyntaxKind.CLIENT_KEYWORD;
            case "ABSTRACT_KEYWORD":
                return SyntaxKind.ABSTRACT_KEYWORD;
            case "IF_KEYWORD":
                return SyntaxKind.IF_KEYWORD;
            case "ELSE_KEYWORD":
                return SyntaxKind.ELSE_KEYWORD;
            case "WHILE_KEYWORD":
                return SyntaxKind.WHILE_KEYWORD;
            case "TRUE_KEYWORD":
                return SyntaxKind.TRUE_KEYWORD;
            case "FALSE_KEYWORD":
                return SyntaxKind.FALSE_KEYWORD;
            case "CHECK_KEYWORD":
                return SyntaxKind.CHECK_KEYWORD;
            case "CHECKPANIC_KEYWORD":
                return SyntaxKind.CHECKPANIC_KEYWORD;
            case "PANIC_KEYWORD":
                return SyntaxKind.PANIC_KEYWORD;
            case "IMPORT_KEYWORD":
                return SyntaxKind.IMPORT_KEYWORD;
            case "VERSION_KEYWORD":
                return SyntaxKind.VERSION_KEYWORD;
            case "AS_KEYWORD":
                return SyntaxKind.AS_KEYWORD;
            case "CONTINUE_KEYWORD":
                return SyntaxKind.CONTINUE_KEYWORD;
            case "BREAK_KEYWORD":
                return SyntaxKind.BREAK_KEYWORD;
            case "RETURN_KEYWORD":
                return SyntaxKind.RETURN_KEYWORD;
            case "SERVICE_KEYWORD":
                return SyntaxKind.SERVICE_KEYWORD;
            case "ON_KEYWORD":
                return SyntaxKind.ON_KEYWORD;
            case "RESOURCE_KEYWORD":
                return SyntaxKind.RESOURCE_KEYWORD;
            case "LISTENER_KEYWORD":
                return SyntaxKind.LISTENER_KEYWORD;
            case "CONST_KEYWORD":
                return SyntaxKind.CONST_KEYWORD;
            case "FINAL_KEYWORD":
                return SyntaxKind.FINAL_KEYWORD;
            case "TYPEOF_KEYWORD":
                return SyntaxKind.TYPEOF_KEYWORD;
            case "ANNOTATION_KEYWORD":
                return SyntaxKind.ANNOTATION_KEYWORD;
            case "IS_KEYWORD":
                return SyntaxKind.IS_KEYWORD;
            case "MAP_KEYWORD":
                return SyntaxKind.MAP_KEYWORD;
            case "FUTURE_KEYWORD":
                return SyntaxKind.FUTURE_KEYWORD;
            case "TYPEDESC_KEYWORD":
                return SyntaxKind.TYPEDESC_KEYWORD;
            case "NULL_KEYWORD":
                return SyntaxKind.NULL_KEYWORD;
            case "LOCK_KEYWORD":
                return SyntaxKind.LOCK_KEYWORD;
            case "VAR_KEYWORD":
                return SyntaxKind.VAR_KEYWORD;
            case "SOURCE_KEYWORD":
                return SyntaxKind.SOURCE_KEYWORD;
            case "WORKER_KEYWORD":
                return SyntaxKind.WORKER_KEYWORD;
            case "PARAMETER_KEYWORD":
                return SyntaxKind.PARAMETER_KEYWORD;
            case "FIELD_KEYWORD":
                return SyntaxKind.FIELD_KEYWORD;
            case "XMLNS_KEYWORD":
                return SyntaxKind.XMLNS_KEYWORD;
            case "INT_KEYWORD":
                return SyntaxKind.INT_KEYWORD;
            case "FLOAT_KEYWORD":
                return SyntaxKind.FLOAT_KEYWORD;
            case "DECIMAL_KEYWORD":
                return SyntaxKind.DECIMAL_KEYWORD;
            case "BOOLEAN_KEYWORD":
                return SyntaxKind.BOOLEAN_KEYWORD;
            case "STRING_KEYWORD":
                return SyntaxKind.STRING_KEYWORD;
            case "BYTE_KEYWORD":
                return SyntaxKind.BYTE_KEYWORD;
            case "XML_KEYWORD":
                return SyntaxKind.XML_KEYWORD;
            case "JSON_KEYWORD":
                return SyntaxKind.JSON_KEYWORD;
            case "HANDLE_KEYWORD":
                return SyntaxKind.HANDLE_KEYWORD;
            case "ANY_KEYWORD":
                return SyntaxKind.ANY_KEYWORD;
            case "ANYDATA_KEYWORD":
                return SyntaxKind.ANYDATA_KEYWORD;
            case "NEVER_KEYWORD":
                return SyntaxKind.NEVER_KEYWORD;
            case "FORK_KEYWORD":
                return SyntaxKind.FORK_KEYWORD;
            case "TRAP_KEYWORD":
                return SyntaxKind.TRAP_KEYWORD;
            case "FOREACH_KEYWORD":
                return SyntaxKind.FOREACH_KEYWORD;
            case "IN_KEYWORD":
                return SyntaxKind.IN_KEYWORD;
            case "TABLE_KEYWORD":
                return SyntaxKind.TABLE_KEYWORD;
            case "KEY_KEYWORD":
                return SyntaxKind.KEY_KEYWORD;
            case "ERROR_KEYWORD":
                return SyntaxKind.ERROR_KEYWORD;
            case "LET_KEYWORD":
                return SyntaxKind.LET_KEYWORD;

            // Operators
            case "PLUS_TOKEN":
                return SyntaxKind.PLUS_TOKEN;
            case "MINUS_TOKEN":
                return SyntaxKind.MINUS_TOKEN;
            case "ASTERISK_TOKEN":
                return SyntaxKind.ASTERISK_TOKEN;
            case "SLASH_TOKEN":
                return SyntaxKind.SLASH_TOKEN;
            case "LT_TOKEN":
                return SyntaxKind.LT_TOKEN;
            case "EQUAL_TOKEN":
                return SyntaxKind.EQUAL_TOKEN;
            case "DOUBLE_EQUAL_TOKEN":
                return SyntaxKind.DOUBLE_EQUAL_TOKEN;
            case "TRIPPLE_EQUAL_TOKEN":
                return SyntaxKind.TRIPPLE_EQUAL_TOKEN;
            case "PERCENT_TOKEN":
                return SyntaxKind.PERCENT_TOKEN;
            case "GT_TOKEN":
                return SyntaxKind.GT_TOKEN;
            case "EQUAL_GT_TOKEN":
                return SyntaxKind.EQUAL_GT_TOKEN;
            case "QUESTION_MARK_TOKEN":
                return SyntaxKind.QUESTION_MARK_TOKEN;
            case "LT_EQUAL_TOKEN":
                return SyntaxKind.LT_EQUAL_TOKEN;
            case "GT_EQUAL_TOKEN":
                return SyntaxKind.GT_EQUAL_TOKEN;
            case "EXCLAMATION_MARK_TOKEN":
                return SyntaxKind.EXCLAMATION_MARK_TOKEN;
            case "NOT_EQUAL_TOKEN":
                return SyntaxKind.NOT_EQUAL_TOKEN;
            case "NOT_DOUBLE_EQUAL_TOKEN":
                return SyntaxKind.NOT_DOUBLE_EQUAL_TOKEN;
            case "BITWISE_AND_TOKEN":
                return SyntaxKind.BITWISE_AND_TOKEN;
            case "BITWISE_XOR_TOKEN":
                return SyntaxKind.BITWISE_XOR_TOKEN;
            case "LOGICAL_AND_TOKEN":
                return SyntaxKind.LOGICAL_AND_TOKEN;
            case "LOGICAL_OR_TOKEN":
                return SyntaxKind.LOGICAL_OR_TOKEN;
            case "NEGATION_TOKEN":
                return SyntaxKind.NEGATION_TOKEN;

            // Separators
            case "OPEN_BRACE_TOKEN":
                return SyntaxKind.OPEN_BRACE_TOKEN;
            case "CLOSE_BRACE_TOKEN":
                return SyntaxKind.CLOSE_BRACE_TOKEN;
            case "OPEN_PAREN_TOKEN":
                return SyntaxKind.OPEN_PAREN_TOKEN;
            case "CLOSE_PAREN_TOKEN":
                return SyntaxKind.CLOSE_PAREN_TOKEN;
            case "OPEN_BRACKET_TOKEN":
                return SyntaxKind.OPEN_BRACKET_TOKEN;
            case "CLOSE_BRACKET_TOKEN":
                return SyntaxKind.CLOSE_BRACKET_TOKEN;
            case "SEMICOLON_TOKEN":
                return SyntaxKind.SEMICOLON_TOKEN;
            case "DOT_TOKEN":
                return SyntaxKind.DOT_TOKEN;
            case "COLON_TOKEN":
                return SyntaxKind.COLON_TOKEN;
            case "COMMA_TOKEN":
                return SyntaxKind.COMMA_TOKEN;
            case "ELLIPSIS_TOKEN":
                return SyntaxKind.ELLIPSIS_TOKEN;
            case "OPEN_BRACE_PIPE_TOKEN":
                return SyntaxKind.OPEN_BRACE_PIPE_TOKEN;
            case "CLOSE_BRACE_PIPE_TOKEN":
                return SyntaxKind.CLOSE_BRACE_PIPE_TOKEN;
            case "PIPE_TOKEN":
                return SyntaxKind.PIPE_TOKEN;
            case "AT_TOKEN":
                return SyntaxKind.AT_TOKEN;
            case "RIGHT_ARROW_TOKEN":
                return SyntaxKind.RIGHT_ARROW_TOKEN;
            case "BACKTICK_TOKEN":
                return SyntaxKind.BACKTICK_TOKEN;
            case "DOUBLE_QUOTE_TOKEN":
                return SyntaxKind.DOUBLE_QUOTE_TOKEN;
            case "SINGLE_QUOTE_TOKEN":
                return SyntaxKind.SINGLE_QUOTE_TOKEN;

            // Expressions
            case "IDENTIFIER_TOKEN":
                return SyntaxKind.IDENTIFIER_TOKEN;
            case "BRACED_EXPRESSION":
                return SyntaxKind.BRACED_EXPRESSION;
            case "BINARY_EXPRESSION":
                return SyntaxKind.BINARY_EXPRESSION;
            case "STRING_LITERAL":
                return SyntaxKind.STRING_LITERAL;
            case "DECIMAL_INTEGER_LITERAL":
                return SyntaxKind.DECIMAL_INTEGER_LITERAL;
            case "HEX_INTEGER_LITERAL":
                return SyntaxKind.HEX_INTEGER_LITERAL;
            case "DECIMAL_FLOATING_POINT_LITERAL":
                return SyntaxKind.DECIMAL_FLOATING_POINT_LITERAL;
            case "HEX_FLOATING_POINT_LITERAL":
                return SyntaxKind.HEX_FLOATING_POINT_LITERAL;
            case "FUNCTION_CALL":
                return SyntaxKind.FUNCTION_CALL;
            case "POSITIONAL_ARG":
                return SyntaxKind.POSITIONAL_ARG;
            case "NAMED_ARG":
                return SyntaxKind.NAMED_ARG;
            case "REST_ARG":
                return SyntaxKind.REST_ARG;
            case "QUALIFIED_NAME_REFERENCE":
                return SyntaxKind.QUALIFIED_NAME_REFERENCE;
            case "FIELD_ACCESS":
                return SyntaxKind.FIELD_ACCESS;
            case "METHOD_CALL":
                return SyntaxKind.METHOD_CALL;
            case "MEMBER_ACCESS":
                return SyntaxKind.MEMBER_ACCESS;
            case "CHECK_EXPRESSION":
                return SyntaxKind.CHECK_EXPRESSION;
            case "MAPPING_CONSTRUCTOR":
                return SyntaxKind.MAPPING_CONSTRUCTOR;
            case "TYPEOF_EXPRESSION":
                return SyntaxKind.TYPEOF_EXPRESSION;
            case "UNARY_EXPRESSION":
                return SyntaxKind.UNARY_EXPRESSION;
            case "TYPE_TEST_EXPRESSION":
                return SyntaxKind.TYPE_TEST_EXPRESSION;
            case "NIL_LITERAL":
                return SyntaxKind.NIL_LITERAL;
            case "SIMPLE_NAME_REFERENCE":
                return SyntaxKind.SIMPLE_NAME_REFERENCE;
            case "TRAP_EXPRESSION":
                return SyntaxKind.TRAP_EXPRESSION;
            case "LIST_CONSTRUCTOR":
                return SyntaxKind.LIST_CONSTRUCTOR;
            case "TYPE_CAST_EXPRESSION":
                return SyntaxKind.TYPE_CAST_EXPRESSION;
            case "TABLE_CONSTRUCTOR":
                return SyntaxKind.TABLE_CONSTRUCTOR;
            case "LET_EXPRESSION":
                return SyntaxKind.LET_EXPRESSION;
            case "RAW_TEMPLATE_EXPRESSION":
                return SyntaxKind.RAW_TEMPLATE_EXPRESSION;
            case "XML_TEMPLATE_EXPRESSION":
                return SyntaxKind.XML_TEMPLATE_EXPRESSION;

            // Actions
            case "REMOTE_METHOD_CALL_ACTION":
                return SyntaxKind.REMOTE_METHOD_CALL_ACTION;
            case "BRACED_ACTION":
                return SyntaxKind.BRACED_ACTION;
            case "CHECK_ACTION":
                return SyntaxKind.CHECK_ACTION;

            // Statements
            case "BLOCK_STATEMENT":
                return SyntaxKind.BLOCK_STATEMENT;
            case "LOCAL_VAR_DECL":
                return SyntaxKind.LOCAL_VAR_DECL;
            case "ASSIGNMENT_STATEMENT":
                return SyntaxKind.ASSIGNMENT_STATEMENT;
            case "IF_ELSE_STATEMENT":
                return SyntaxKind.IF_ELSE_STATEMENT;
            case "ELSE_BLOCK":
                return SyntaxKind.ELSE_BLOCK;
            case "WHILE_STATEMENT":
                return SyntaxKind.WHILE_STATEMENT;
            case "CALL_STATEMENT":
                return SyntaxKind.CALL_STATEMENT;
            case "PANIC_STATEMENT":
                return SyntaxKind.PANIC_STATEMENT;
            case "CONTINUE_STATEMENT":
                return SyntaxKind.CONTINUE_STATEMENT;
            case "BREAK_STATEMENT":
                return SyntaxKind.BREAK_STATEMENT;
            case "RETURN_STATEMENT":
                return SyntaxKind.RETURN_STATEMENT;
            case "COMPOUND_ASSIGNMENT_STATEMENT":
                return SyntaxKind.COMPOUND_ASSIGNMENT_STATEMENT;
            case "LOCAL_TYPE_DEFINITION_STATEMENT":
                return SyntaxKind.LOCAL_TYPE_DEFINITION_STATEMENT;
            case "ACTION_STATEMENT":
                return SyntaxKind.ACTION_STATEMENT;
            case "LOCK_STATEMENT":
                return SyntaxKind.LOCK_STATEMENT;
            case "FORK_STATEMENT":
                return SyntaxKind.FORK_STATEMENT;
            case "FOREACH_STATEMENT":
                return SyntaxKind.FOREACH_STATEMENT;

            // Types
            case "TYPE_DESC":
                return SyntaxKind.TYPE_DESC;
            case "INT_TYPE_DESC":
                return SyntaxKind.INT_TYPE_DESC;
            case "FLOAT_TYPE_DESC":
                return SyntaxKind.FLOAT_TYPE_DESC;
            case "DECIMAL_TYPE_DESC":
                return SyntaxKind.DECIMAL_TYPE_DESC;
            case "BOOLEAN_TYPE_DESC":
                return SyntaxKind.BOOLEAN_TYPE_DESC;
            case "STRING_TYPE_DESC":
                return SyntaxKind.STRING_TYPE_DESC;
            case "BYTE_TYPE_DESC":
                return SyntaxKind.BYTE_TYPE_DESC;
            case "XML_TYPE_DESC":
                return SyntaxKind.XML_TYPE_DESC;
            case "JSON_TYPE_DESC":
                return SyntaxKind.JSON_TYPE_DESC;
            case "HANDLE_TYPE_DESC":
                return SyntaxKind.HANDLE_TYPE_DESC;
            case "ANY_TYPE_DESC":
                return SyntaxKind.ANY_TYPE_DESC;
            case "ANYDATA_TYPE_DESC":
                return SyntaxKind.ANYDATA_TYPE_DESC;
            case "NEVER_TYPE_DESC":
                return SyntaxKind.NEVER_TYPE_DESC;
            case "NIL_TYPE_DESC":
                return SyntaxKind.NIL_TYPE_DESC;
            case "OPTIONAL_TYPE_DESC":
                return SyntaxKind.OPTIONAL_TYPE_DESC;
            case "ARRAY_TYPE_DESC":
                return SyntaxKind.ARRAY_TYPE_DESC;
            case "RECORD_TYPE_DESC":
                return SyntaxKind.RECORD_TYPE_DESC;
            case "OBJECT_TYPE_DESC":
                return SyntaxKind.OBJECT_TYPE_DESC;
            case "UNION_TYPE_DESC":
                return SyntaxKind.UNION_TYPE_DESC;
            case "ERROR_TYPE_DESC":
                return SyntaxKind.ERROR_TYPE_DESC;
            case "EXPLICIT_TYPE_PARAMS":
                return SyntaxKind.EXPLICIT_TYPE_PARAMS;

            // Others
            case "FUNCTION_BODY_BLOCK":
                return SyntaxKind.FUNCTION_BODY_BLOCK;
            case "LIST":
                return SyntaxKind.LIST;
            case "RETURN_TYPE_DESCRIPTOR":
                return SyntaxKind.RETURN_TYPE_DESCRIPTOR;
            case "EXTERNAL_FUNCTION_BODY":
                return SyntaxKind.EXTERNAL_FUNCTION_BODY;
            case "REQUIRED_PARAM":
                return SyntaxKind.REQUIRED_PARAM;
            case "DEFAULTABLE_PARAM":
                return SyntaxKind.DEFAULTABLE_PARAM;
            case "REST_PARAM":
                return SyntaxKind.REST_PARAM;
            case "RECORD_FIELD":
                return SyntaxKind.RECORD_FIELD;
            case "RECORD_FIELD_WITH_DEFAULT_VALUE":
                return SyntaxKind.RECORD_FIELD_WITH_DEFAULT_VALUE;
            case "TYPE_REFERENCE":
                return SyntaxKind.TYPE_REFERENCE;
            case "RECORD_REST_TYPE":
                return SyntaxKind.RECORD_REST_TYPE;
            case "OBJECT_FIELD":
                return SyntaxKind.OBJECT_FIELD;
            case "IMPORT_ORG_NAME":
                return SyntaxKind.IMPORT_ORG_NAME;
            case "MODULE_NAME":
                return SyntaxKind.MODULE_NAME;
            case "SUB_MODULE_NAME":
                return SyntaxKind.SUB_MODULE_NAME;
            case "IMPORT_VERSION":
                return SyntaxKind.IMPORT_VERSION;
            case "IMPORT_SUB_VERSION":
                return SyntaxKind.IMPORT_SUB_VERSION;
            case "IMPORT_PREFIX":
                return SyntaxKind.IMPORT_PREFIX;
            case "SPECIFIC_FIELD":
                return SyntaxKind.SPECIFIC_FIELD;
            case "COMPUTED_NAME_FIELD":
                return SyntaxKind.COMPUTED_NAME_FIELD;
            case "SPREAD_FIELD":
                return SyntaxKind.SPREAD_FIELD;
            case "SERVICE_BODY":
                return SyntaxKind.SERVICE_BODY;
            case "EXPRESSION_LIST_ITEM":
                return SyntaxKind.EXPRESSION_LIST_ITEM;
            case "ARRAY_DIMENSION":
                return SyntaxKind.ARRAY_DIMENSION;
            case "METADATA":
                return SyntaxKind.METADATA;
            case "ANNOTATION":
                return SyntaxKind.ANNOTATION;
            case "PARAMETERIZED_TYPE_DESC":
                return SyntaxKind.PARAMETERIZED_TYPE_DESC;
            case "ANNOTATION_ATTACH_POINT":
                return SyntaxKind.ANNOTATION_ATTACH_POINT;
            case "NAMED_WORKER_DECLARATION":
                return SyntaxKind.NAMED_WORKER_DECLARATION;
            case "NAMED_WORKER_DECLARATOR":
                return SyntaxKind.NAMED_WORKER_DECLARATOR;
            case "DOCUMENTATION_STRING":
                return SyntaxKind.DOCUMENTATION_STRING;
            case "DOCUMENTATION_LINE":
                return SyntaxKind.DOCUMENTATION_LINE;
            case "TYPE_CAST_PARAM":
                return SyntaxKind.TYPE_CAST_PARAM;
            case "KEY_SPECIFIER":
                return SyntaxKind.KEY_SPECIFIER;
            case "ERROR_TYPE_PARAMS":
                return SyntaxKind.ERROR_TYPE_PARAMS;
            case "LET_VAR_DECL":
                return SyntaxKind.LET_VAR_DECL;

            // XML template
            case "XML_ELEMENT":
                return SyntaxKind.XML_ELEMENT;
            case "XML_EMPTY_ELEMENT":
                return SyntaxKind.XML_EMPTY_ELEMENT;
            case "XML_ELEMENT_START_TAG":
                return SyntaxKind.XML_ELEMENT_START_TAG;
            case "XML_ELEMENT_END_TAG":
                return SyntaxKind.XML_ELEMENT_END_TAG;
            case "XML_TEXT":
                return SyntaxKind.XML_TEXT;
            case "XML_PI":
                return SyntaxKind.XML_PI;
            case "XML_ATTRIBUTE":
                return SyntaxKind.XML_ATTRIBUTE;
            case "XML_SIMPLE_NAME":
                return SyntaxKind.XML_SIMPLE_NAME;
            case "XML_QUALIFIED_NAME":
                return SyntaxKind.XML_QUALIFIED_NAME;
            case "INTERPOLATION":
                return SyntaxKind.INTERPOLATION;
            case "INTERPOLATION_START_TOKEN":
                return SyntaxKind.INTERPOLATION_START_TOKEN;
            case "XML_COMMENT":
                return SyntaxKind.XML_COMMENT;
            case "XML_COMMENT_START_TOKEN":
                return SyntaxKind.XML_COMMENT_START_TOKEN;
            case "XML_COMMENT_END_TOKEN":
                return SyntaxKind.XML_COMMENT_END_TOKEN;
            case "XML_TEXT_CONTENT":
                return SyntaxKind.XML_TEXT_CONTENT;
            case "XML_PI_START_TOKEN":
                return SyntaxKind.XML_PI_START_TOKEN;
            case "XML_PI_END_TOKEN":
                return SyntaxKind.XML_PI_END_TOKEN;
            case "XML_ATTRIBUTE_VALUE":
                return SyntaxKind.XML_ATTRIBUTE_VALUE;
            case "TEMPLATE_STRING":
                return SyntaxKind.TEMPLATE_STRING;

            // Trivia
            case "EOF_TOKEN":
                return SyntaxKind.EOF_TOKEN;
            case "END_OF_LINE_TRIVIA":
                return SyntaxKind.END_OF_LINE_TRIVIA;
            case "WHITESPACE_TRIVIA":
                return SyntaxKind.WHITESPACE_TRIVIA;
            case "COMMENT":
                return SyntaxKind.COMMENT;
            case "INVALID":
                return SyntaxKind.INVALID;

            // Unsupported
            default:
                throw new UnsupportedOperationException("cannot find syntax kind: " + kind);
        }
    }
}
