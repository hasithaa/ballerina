/**
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import React from 'react';
import PropTypes from 'prop-types';
import breakpointHoc from 'src/plugins/debugger/views/BreakpointHoc';
import SimpleBBox from 'plugins/ballerina/model/view/simple-bounding-box';
import ExpressionEditor from 'plugins/ballerina/expression-editor/expression-editor-utils';
import Node from '../../../../../model/tree/node';
import DropZone from '../../../../../drag-drop/DropZone';
import './compound-statement-decorator.css';
import ActionBox from '../decorators/action-box';
import ActiveArbiter from '../decorators/active-arbiter';
import Breakpoint from '../decorators/breakpoint';
import { getComponentForNodeArray } from './../../../../diagram-util';

/**
 * Wraps other UI elements and provide box with a heading.
 * Enrich elements with a action box and expression editors.
 */
class FinallyStatementDecorator extends React.Component {

    /**
     * Initialize the block decorator.
     */
    constructor() {
        super();
        this.state = {
            active: 'hidden',
        };
        this.onDelete = this.onDelete.bind(this);
        this.onJumpToCodeLine = this.onJumpToCodeLine.bind(this);
        this.setActionVisibilityFalse = this.setActionVisibility.bind(this, false);
        this.setActionVisibilityTrue = this.setActionVisibility.bind(this, true);
        this.openExpressionEditor = e => this.openEditor(this.props.expression, this.props.editorOptions, e);
        this.openParameterEditor = e => this.openEditor(this.props.parameterEditorOptions.value,
            this.props.parameterEditorOptions, e);
    }
    /**
     * Handles click event of breakpoint, adds/remove breakpoint from the node when click event fired
     *
     */
    onBreakpointClick() {
        const { model } = this.props;
        const { isBreakpoint = false } = model;
        if (isBreakpoint) {
            model.removeBreakpoint();
        } else {
            model.addBreakpoint();
        }
    }

    /**
     * Removes self on delete button click. Note that model is retried form dropTarget for
     * backward compatibility with old components written when model was not required.
     * @returns {void}
     */
    onDelete() {
        const model = this.props.model || this.props.dropTarget;
        model.remove();
    }
    /**
     * Navigates to codeline in the source view from the design view node
     *
     */
    onJumpToCodeLine() {
        const { editor } = this.context;
        editor.goToSource(this.props.model);
    }

    /**
     * Call-back for when a new value is entered via expression editor.
     */
    onUpdate() {
        // TODO: implement validate logic.
    }

    /**
     * Shows the action box, depending on whether on child element, delays display.
     * @param {boolean} show - Display action box.
     * @param {MouseEvent} e - Mouse move event from moving on to or out of statement.
     */
    setActionVisibility(show, e) {
        e.stopPropagation();
        if (show) {
            const isInChildStatement = this.isInFocusableChild(e.target);
            const isFromChildStatement = this.isInFocusableChild(e.relatedTarget);

            if (!isInChildStatement) {
                if (isFromChildStatement) {
                    this.context.activeArbiter.readyToDelayedActivate(this);
                } else {
                    this.context.activeArbiter.readyToActivate(this);
                }
            }
        } else {
            let elm = e.relatedTarget;
            let isInMe = false;
            while (elm && elm.getAttribute) {
                if (elm === this.myRoot) {
                    isInMe = true;
                }
                elm = elm.parentNode;
            }
            if (!isInMe) {
                this.context.activeArbiter.readyToDeactivate(this);
            }
        }
    }

    /**
     * True if the given element is a child of this element that has it's own focus.
     * @private
     * @param {HTMLElement} elmToCheck - child to be checked.
     * @return {boolean} True if child is focusable.
     */
    isInFocusableChild(elmToCheck) {
        const regex = new RegExp('(^|\\s)((compound-)?statement|life-line-group)(\\s|$)');
        let isInStatement = false;
        let elm = elmToCheck;
        while (elm && elm !== this.myRoot && elm.getAttribute) {
            if (regex.test(elm.getAttribute('class'))) {
                isInStatement = true;
            }
            elm = elm.parentNode;
        }
        return isInStatement;
    }

    /**
     * renders an ExpressionEditor in the header space.
     * @param {string} value - Initial value.
     * @param {object} options - options to be sent to ExpressionEditor.
     */
    openEditor(value, options) {
        const packageScope = this.context.environment;
        if (value && options) {
            new ExpressionEditor(
                this.conditionBox,
                this.onUpdate.bind(this),
                options,
                packageScope).render(this.context.getOverlayContainer());
        }
    }

    /**
     * Render breakpoint element.
     * @private
     * @return {XML} React element of the breakpoint.
     */
    renderBreakpointIndicator() {
        const breakpointSize = 14;
        const { bBox } = this.props;
        const breakpointHalf = breakpointSize / 2;
        const pointX = bBox.getRight() - breakpointHalf;
        const { model: { viewState } } = this.props;
        const statementBBox = viewState.components['statement-box'];
        const pointY = statementBBox.y - breakpointHalf;
        return (
            <Breakpoint
                x={pointX}
                y={pointY}
                size={breakpointSize}
                isBreakpoint={this.props.isBreakpoint}
                onClick={() => this.props.onBreakpointClick()}
            />
        );
    }

    /**
     * Override the rendering logic.
     * @returns {XML} rendered component.
     */
    render() {
        const { bBox, isBreakpoint, isDebugHit } = this.props;
        const { designer } = this.context;

        const model = this.props.model;
        const viewState = model.viewState;
        const titleH = this.context.designer.config.compoundStatement.heading.height;
        const titleW = this.context.designer.config.compoundStatement.heading.width;
        const statementBBox = viewState.components['statement-box'];
        const gapLeft = this.context.designer.config.compoundStatement.padding.left;
        const gapTop = this.context.designer.config.compoundStatement.padding.top;


        // Defining coordinates of the diagram
        // (x,y)
        // (P1)        (P2)|---------|(P3)      (P4)
        //       |---------| finally |----------|
        // (P11) |         |____ ____|__________| (statementBox)
        //       |              |(p8)           |
        //       |              |               |
        //       |         true |               |
        //       |            __|__ (p12)     __|__
        //       |            a = 1;           a = 5;
        //       |              |               |
        //  (P7) |               (p10)          |
        //       |                              |
        //       |_____________(P6)_____________| (P5)
        //                      |

        const p1X = bBox.x - gapLeft;
        const p1Y = bBox.y + gapTop; // - titleH;

        const p2X = bBox.x - (titleW / 2);
        const p2Y = p1Y + (titleH / 2);

        const p3X = bBox.x + (titleW / 2);
        const p3Y = p2Y;

        const p4X = p1X + gapLeft + statementBBox.w;
        const p4Y = p2Y;

        const p5X = p4X;
        const p5Y = bBox.y + bBox.h;

        const p6X = bBox.x;
        const p6Y = p5Y;

        const p8X = bBox.x;
        const p8Y = p2Y + (titleH / 2);

        const p9X = p8X;
        const p9Y = p8Y - titleH;

        const p11X = p1X;
        const p11Y = p1Y + (titleH / 2);

        const p12X = p8X;
        const p12Y = p8Y + this.context.designer.config.compoundStatement.heading.gap;

        this.conditionBox = new SimpleBBox(p2X, (p2Y - (this.context.designer.config.statement.height / 2)),
            statementBBox.w, this.context.designer.config.statement.height);

        const actionBoxBbox = new SimpleBBox();
        actionBoxBbox.w = (3 * designer.config.actionBox.width) / 4;
        actionBoxBbox.h = designer.config.actionBox.height;
        actionBoxBbox.x = p8X - (actionBoxBbox.w / 2);
        actionBoxBbox.y = p8Y;

        let statementRectClass = 'statement-title-rect';
        if (isDebugHit) {
            statementRectClass = `${statementRectClass} debug-hit`;
        }

        const body = getComponentForNodeArray(this.props.model);

        return (
            <g
                onMouseOut={this.setActionVisibilityFalse}
                onMouseOver={this.setActionVisibilityTrue}
                ref={(group) => {
                    this.myRoot = group;
                }}
            >
                <polyline
                    points={`${p3X},${p3Y} ${p4X},${p4Y} ${p5X},${p5Y} ${p6X},${p6Y}`}
                    className='background-empty-rect'
                />
                <rect
                    x={p2X}
                    y={p1Y}
                    width={titleW}
                    height={titleH}
                    className={statementRectClass}
                    rx='5'
                    ry='5'
                />
                <text
                    x={p8X}
                    y={p2Y}
                    className='statement-title-text'
                >finally
                </text>
                <DropZone
                    x={p11X}
                    y={p11Y}
                    width={statementBBox.w}
                    height={statementBBox.h}
                    baseComponent='rect'
                    dropTarget={this.props.model.body}
                    enableDragBg
                    enableCenterOverlayLine={!this.props.disableDropzoneMiddleLineOverlay}
                />
                { isBreakpoint && this.renderBreakpointIndicator() }
                {this.props.children}
                {body}
                <ActionBox
                    bBox={actionBoxBbox}
                    show={this.state.active}
                    isBreakpoint={isBreakpoint}
                    onDelete={() => this.onDelete()}
                    onJumptoCodeLine={() => this.onJumpToCodeLine()}
                    onBreakpointClick={() => this.props.onBreakpointClick()}
                    disableButtons={this.props.disableButtons}
                />
                <rect
                    x={bBox.x}
                    y={bBox.y}
                    width={bBox.w}
                    height={bBox.h}
                    stroke='green'
                    strokeWidth='3'
                    fillOpacity='0'
                />
                <rect
                    x={statementBBox.x}
                    y={statementBBox.y}
                    width={statementBBox.w}
                    height={statementBBox.h}
                    stroke='yellow'
                    fillOpacity='0'
                    strokeWidth='2'
                />
            </g>);
    }
}

FinallyStatementDecorator.defaultProps = {
    draggable: null,
    children: null,
    undeletable: false,
    editorOptions: null,
    parameterEditorOptions: null,
    utilities: null,
    parameterBbox: null,
    disableButtons: {
        debug: false,
        delete: false,
        jump: false,
    },
    disableDropzoneMiddleLineOverlay: false,
    isDebugHit: false,
};

FinallyStatementDecorator.propTypes = {
    model: PropTypes.instanceOf(Node).isRequired,
    children: PropTypes.arrayOf(PropTypes.node),
    bBox: PropTypes.instanceOf(SimpleBBox).isRequired,
    dropTarget: PropTypes.instanceOf(Node).isRequired,
    expression: PropTypes.shape({
        text: PropTypes.string,
    }).isRequired,
    editorOptions: PropTypes.shape({
        propertyType: PropTypes.string,
        key: PropTypes.string,
        model: PropTypes.instanceOf(Node),
        getterMethod: PropTypes.func,
        setterMethod: PropTypes.func,
    }),
    parameterEditorOptions: PropTypes.shape({
        propertyType: PropTypes.string,
        key: PropTypes.string,
        value: PropTypes.string,
        model: PropTypes.instanceOf(Node),
        getterMethod: PropTypes.func,
        setterMethod: PropTypes.func,
    }),
    onBreakpointClick: PropTypes.func.isRequired,
    isBreakpoint: PropTypes.bool.isRequired,
    disableButtons: PropTypes.shape({
        debug: PropTypes.bool.isRequired,
        delete: PropTypes.bool.isRequired,
        jump: PropTypes.bool.isRequired,
    }),
    disableDropzoneMiddleLineOverlay: PropTypes.bool,
    isDebugHit: PropTypes.bool,
};

FinallyStatementDecorator.contextTypes = {
    getOverlayContainer: PropTypes.instanceOf(Object).isRequired,
    environment: PropTypes.instanceOf(Object).isRequired,
    editor: PropTypes.instanceOf(Object).isRequired,
    mode: PropTypes.string,
    activeArbiter: PropTypes.instanceOf(ActiveArbiter).isRequired,
    designer: PropTypes.instanceOf(Object),
};

export default breakpointHoc(FinallyStatementDecorator);
