import { LitElement, html, css } from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import { queries } from 'build-time-data';
import '@vaadin/grid';
import '@vaadin/text-field';
import '@vaadin/details';

/**
 * DevUI component displaying Qubit lambda queries and their generated executors.
 */
export class QwcQubitQueries extends LitElement {

    static styles = css`
        :host {
            display: flex;
            flex-direction: column;
            gap: 10px;
            height: 100%;
            overflow: hidden;
        }

        .queries-table {
            flex: 1;
            overflow: auto;
        }

        .topBar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0 10px;
        }

        .searchField {
            width: 300px;
        }

        .summary {
            color: var(--lumo-secondary-text-color);
            font-size: var(--lumo-font-size-s);
        }

        code {
            font-size: 85%;
            background-color: var(--lumo-contrast-5pct);
            padding: 2px 6px;
            border-radius: 4px;
        }

        .query-type {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: 500;
        }

        .query-type-list {
            background-color: var(--lumo-success-color-10pct);
            color: var(--lumo-success-text-color);
        }

        .query-type-count {
            background-color: var(--lumo-primary-color-10pct);
            color: var(--lumo-primary-text-color);
        }

        .query-type-join {
            background-color: var(--lumo-warning-color-10pct);
            color: var(--lumo-warning-text-color);
        }

        .query-type-group {
            background-color: var(--lumo-error-color-10pct);
            color: var(--lumo-error-text-color);
        }

        .query-type-aggregation {
            background-color: #e1bee7;
            color: #6a1b9a;
        }

        .entity-info {
            display: flex;
            flex-direction: column;
            gap: 2px;
        }

        .entity-name {
            font-weight: 500;
        }

        .entity-package {
            font-size: 11px;
            color: var(--lumo-secondary-text-color);
        }

        .generated-class {
            font-family: monospace;
            font-size: 12px;
            word-break: break-all;
            cursor: pointer;
            transition: color 0.2s ease;
        }

        .generated-class:hover {
            color: var(--lumo-primary-color);
            text-decoration: underline;
        }

        .query-id-cell {
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .query-id-cell:hover code {
            background-color: var(--lumo-primary-color-10pct);
            color: var(--lumo-primary-text-color);
        }

        .captured-badge {
            background-color: var(--lumo-contrast-10pct);
            padding: 2px 6px;
            border-radius: 10px;
            font-size: 12px;
        }

        .jpql-panel {
            padding: 10px;
            background-color: var(--lumo-contrast-5pct);
            border-radius: 8px;
            border-left: 4px solid var(--lumo-primary-color);
            flex-shrink: 0;
        }

        .jpql-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
        }

        .jpql-title {
            font-weight: 600;
            color: var(--lumo-primary-text-color);
        }

        .jpql-close {
            cursor: pointer;
            color: var(--lumo-secondary-text-color);
            font-size: 18px;
            padding: 4px 8px;
            border-radius: 4px;
            transition: background-color 0.2s ease;
        }

        .jpql-close:hover {
            background-color: var(--lumo-contrast-10pct);
        }

        .jpql-content {
            font-family: monospace;
            font-size: 13px;
            background-color: var(--lumo-base-color);
            padding: 12px;
            border-radius: 4px;
            white-space: pre-wrap;
            word-break: break-word;
            line-height: 1.5;
        }

        .info-footer {
            display: flex;
            flex-wrap: wrap;
            font-size: 11px;
            color: var(--lumo-secondary-text-color);
            margin-top: 10px;
            padding-top: 10px;
            border-top: 1px solid var(--lumo-contrast-10pct);
        }

        .info-footer-item {
            flex: 1 1 50%;
            min-width: 200px;
            display: flex;
            flex-wrap: wrap;
            align-items: baseline;
            gap: 4px;
        }

        .info-label {
            font-size: 10px;
            color: var(--lumo-tertiary-text-color);
        }

        .info-value {
            font-family: monospace;
            word-break: break-all;
        }

        .no-jpql {
            color: var(--lumo-secondary-text-color);
            font-style: italic;
        }

        .lambda-section {
            margin-top: 15px;
            padding-top: 15px;
            border-top: 1px solid var(--lumo-contrast-10pct);
        }

        .lambda-title {
            font-weight: 600;
            color: var(--lumo-success-text-color);
            margin-bottom: 8px;
        }

        .lambda-content {
            font-family: monospace;
            font-size: 13px;
            background-color: var(--lumo-base-color);
            padding: 10px 12px;
            border-radius: 4px;
            border-left: 3px solid var(--lumo-success-color);
        }

        .lambda-label {
            font-size: 11px;
            color: var(--lumo-secondary-text-color);
            margin-bottom: 4px;
        }

        .click-hint {
            font-size: 11px;
            color: var(--lumo-secondary-text-color);
            margin-left: 10px;
        }

        /* Side-by-side comparison table for Lambda and JPQL */
        .comparison-container {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            margin-top: 10px;
        }

        /* Responsive: stack vertically on small screens */
        @media (max-width: 900px) {
            .comparison-container {
                grid-template-columns: 1fr;
            }
        }

        .comparison-column {
            display: flex;
            flex-direction: column;
            min-width: 0; /* Allow shrinking */
        }

        .comparison-header {
            font-weight: 600;
            font-size: 13px;
            padding: 8px 12px;
            border-radius: 4px 4px 0 0;
            margin-bottom: 0;
        }

        .comparison-header-lambda {
            background-color: var(--lumo-success-color-10pct);
            color: var(--lumo-success-text-color);
            border-left: 3px solid var(--lumo-success-color);
        }

        .comparison-header-jpql {
            background-color: var(--lumo-primary-color-10pct);
            color: var(--lumo-primary-text-color);
            border-left: 3px solid var(--lumo-primary-color);
        }

        .comparison-body {
            font-family: monospace;
            font-size: 13px;
            background-color: var(--lumo-base-color);
            padding: 12px;
            border-radius: 0 0 4px 4px;
            white-space: pre-wrap;
            word-break: break-word;
            line-height: 1.5;
            overflow-x: auto;
        }

        .comparison-body-lambda {
            border-left: 3px solid var(--lumo-success-color);
        }

        .comparison-body-jpql {
            border-left: 3px solid var(--lumo-primary-color);
        }

        .lambda-sublabel {
            font-size: 10px;
            color: var(--lumo-secondary-text-color);
            margin-bottom: 4px;
            margin-top: 8px;
        }

        .lambda-sublabel:first-child {
            margin-top: 0;
        }

        .lambda-code {
            background-color: var(--lumo-contrast-5pct);
            padding: 6px 10px;
            border-radius: 4px;
            margin-bottom: 4px;
        }

        .method-chain {
            font-family: monospace;
            font-size: 13px;
            line-height: 1.6;
        }

        .chain-entity {
            color: var(--lumo-primary-color);
            font-weight: 600;
        }

        .chain-method {
            color: var(--lumo-secondary-text-color);
        }

        .chain-lambda {
            color: var(--lumo-success-text-color);
            background-color: var(--lumo-success-color-10pct);
            padding: 2px 6px;
            border-radius: 4px;
        }

        .chain-terminal {
            color: var(--lumo-tertiary-text-color);
            font-style: italic;
        }

        /* Selected row styling */
        vaadin-grid::part(selected-row) {
            background-color: var(--lumo-primary-color-10pct);
        }

        vaadin-grid::part(selected-row-cell) {
            background-color: var(--lumo-primary-color-10pct);
        }
    `;

    static properties = {
        _filteredQueries: { state: true },
        _selectedQuery: { state: true }
    };

    constructor() {
        super();
        this._filteredQueries = queries;
        this._selectedQuery = null;
    }

    render() {
        const searchBox = html`
            <vaadin-text-field
                class="searchField"
                placeholder="Search queries..."
                clear-button-visible
                @value-changed="${e => {
                    const searchTerm = (e.detail.value || '').trim().toLowerCase();
                    this._filteredQueries = queries.filter(q => this._matchesTerm(q, searchTerm));
                }}">
                <vaadin-icon slot="prefix" icon="font-awesome-solid:magnifying-glass"></vaadin-icon>
            </vaadin-text-field>
        `;

        const summary = html`
            <span class="summary">
                ${this._filteredQueries.length} of ${queries.length} queries
                <span class="click-hint">(click row to view JPQL)</span>
            </span>
        `;

        return html`
            <div class="topBar">
                ${searchBox}
                ${summary}
            </div>
            ${this._selectedQuery ? this._renderJpqlPanel() : ''}
            <vaadin-grid
                .items="${this._filteredQueries}"
                .selectedItems="${this._selectedQuery ? [this._selectedQuery] : []}"
                class="queries-table"
                theme="no-border row-stripes"
                @active-item-changed="${this._onRowClick}"
                all-rows-visible>
                <vaadin-grid-column
                    header="Entity"
                    auto-width
                    flex-grow="0"
                    ${columnBodyRenderer(this._entityRenderer, [])}
                    resizable>
                </vaadin-grid-column>
                <vaadin-grid-column
                    header="Query type"
                    auto-width
                    flex-grow="0"
                    ${columnBodyRenderer(this._queryTypeRenderer, [])}
                    resizable>
                </vaadin-grid-column>
                <vaadin-grid-column
                    header="Captured vars"
                    auto-width
                    flex-grow="0"
                    text-align="center"
                    ${columnBodyRenderer(this._capturedVarsRenderer, [])}
                    resizable>
                </vaadin-grid-column>
                <vaadin-grid-column
                    header="Query ID"
                    flex-grow="2"
                    ${columnBodyRenderer(this._queryIdRenderer, [])}
                    resizable>
                </vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _renderJpqlPanel() {
        const query = this._selectedQuery;
        const hasJpql = query.jpql && query.jpql.trim() !== '' && !query.jpql.startsWith('SELECT e FROM Entity');
        const hasPredicate = query.predicateLambda && query.predicateLambda.trim() !== '';
        const hasProjection = query.projectionLambda && query.projectionLambda.trim() !== '';
        const hasSortLambda = query.sortLambda && query.sortLambda.trim() !== '';
        const hasAggregationLambda = query.aggregationLambda && query.aggregationLambda.trim() !== '';
        const hasGroupByKeyLambda = query.groupByKeyLambda && query.groupByKeyLambda.trim() !== '';
        const hasJoinRelationshipLambda = query.joinRelationshipLambda && query.joinRelationshipLambda.trim() !== '';
        const hasLambda = hasPredicate || hasProjection || hasSortLambda || hasAggregationLambda || hasGroupByKeyLambda || hasJoinRelationshipLambda;

        // Always show method chain for aggregation, join, and group queries (they have meaningful structure even without lambdas)
        const queryType = query.queryType || '';
        const isSpecialQuery = queryType === 'Aggregation' || queryType.includes('Join') || queryType.includes('Group');
        const showMethodChain = hasLambda || isSpecialQuery;

        // Build the method chain representation
        const methodChain = this._buildMethodChain(query, hasPredicate, hasProjection);

        return html`
            <div class="jpql-panel">
                <div class="jpql-header">
                    <span class="jpql-title">Query Details</span>
                    <span class="jpql-close" @click="${this._closeJpqlPanel}" title="Close">✕</span>
                </div>

                <div class="comparison-container">
                    <div class="comparison-column">
                        <div class="comparison-header comparison-header-lambda">Lambda expression</div>
                        <div class="comparison-body comparison-body-lambda">${showMethodChain ? html`${methodChain}` : html`<span class="no-jpql">No lambda expression available</span>`}</div>
                    </div>
                    <div class="comparison-column">
                        <div class="comparison-header comparison-header-jpql">Generated JPQL</div>
                        <div class="comparison-body comparison-body-jpql">${hasJpql ? html`${this._formatJpql(query.jpql)}` : html`<span class="no-jpql">JPQL not available (deduplicated or complex query)</span>`}</div>
                    </div>
                </div>

                <div class="info-footer">
                    <div class="info-footer-item">
                        <span class="info-label">Query ID:</span>
                        <span class="info-value">${query.queryId}</span>
                    </div>
                    <div class="info-footer-item">
                        <span class="info-label">Generated class:</span>
                        <span class="info-value">${query.generatedClassName}</span>
                    </div>
                </div>
            </div>
        `;
    }

    _buildMethodChain(query, hasPredicate, hasProjection) {
        const entity = query.entityName;
        const queryType = query.queryType;
        const jpql = query.jpql || '';
        const isGroupQuery = queryType.includes('Group');
        const isJoinQuery = queryType.includes('Join');
        const isAggregation = queryType === 'Aggregation';
        const isCount = queryType === 'Count' || queryType.includes('Count');

        // Check for new data fields
        const hasSortLambda = query.sortLambda && query.sortLambda.trim() !== '';
        const hasAggregationLambda = query.aggregationLambda && query.aggregationLambda.trim() !== '';
        const hasGroupByKeyLambda = query.groupByKeyLambda && query.groupByKeyLambda.trim() !== '';
        const hasJoinRelationshipLambda = query.joinRelationshipLambda && query.joinRelationshipLambda.trim() !== '';
        const hasHavingLambda = query.havingLambda && query.havingLambda.trim() !== '';

        // Build chain parts
        const parts = [];

        // Entity entry point
        parts.push(html`<span class="chain-entity">${entity}</span>`);

        if (isGroupQuery) {
            // Group queries: Entity.groupBy(...).having(...).select(...) or .selectKey()
            if (hasGroupByKeyLambda) {
                parts.push(html`<span class="chain-method">.groupBy(</span><span class="chain-lambda">${query.groupByKeyLambda}</span><span class="chain-method">)</span>`);
            } else {
                parts.push(html`<span class="chain-method">.groupBy(...)</span>`);
            }
            // Use havingLambda from backend if available, otherwise fall back to JPQL detection
            if (hasHavingLambda) {
                parts.push(html`<span class="chain-method">.having(</span><span class="chain-lambda">${query.havingLambda}</span><span class="chain-method">)</span>`);
            } else if (hasPredicate && jpql.includes(' HAVING ')) {
                parts.push(html`<span class="chain-method">.having(</span><span class="chain-lambda">${query.predicateLambda}</span><span class="chain-method">)</span>`);
            }
            // Use isSelectKey from backend to determine if selectKey() or select() was called
            if (query.isSelectKey) {
                parts.push(html`<span class="chain-method">.selectKey()</span>`);
            } else if (hasProjection) {
                parts.push(html`<span class="chain-method">.select(</span><span class="chain-lambda">${query.projectionLambda}</span><span class="chain-method">)</span>`);
            }
        } else if (isJoinQuery) {
            // Join queries: Entity.join(...).where(...).distinct()
            const joinMethod = queryType.includes('Left') ? 'leftJoin' : 'join';
            if (hasJoinRelationshipLambda) {
                parts.push(html`<span class="chain-method">.${joinMethod}(</span><span class="chain-lambda">${query.joinRelationshipLambda}</span><span class="chain-method">)</span>`);
            } else {
                parts.push(html`<span class="chain-method">.${joinMethod}(...)</span>`);
            }
            if (hasPredicate) {
                parts.push(html`<span class="chain-method">.where(</span><span class="chain-lambda">${query.predicateLambda}</span><span class="chain-method">)</span>`);
            }
            if (hasProjection) {
                parts.push(html`<span class="chain-method">.select(</span><span class="chain-lambda">${query.projectionLambda}</span><span class="chain-method">)</span>`);
            }
            // Handle distinct for join queries
            if (query.hasDistinct) {
                parts.push(html`<span class="chain-method">.distinct()</span>`);
            }
        } else if (isAggregation) {
            // Aggregation: Entity.where(...).max/min/avg/sum(...)
            // Use aggregationType from backend if available, otherwise detect from JPQL
            const aggMethod = this._getAggregationMethod(query.aggregationType, jpql);
            if (hasPredicate) {
                parts.push(html`<span class="chain-method">.where(</span><span class="chain-lambda">${query.predicateLambda}</span><span class="chain-method">)</span>`);
            }
            if (hasAggregationLambda) {
                parts.push(html`<span class="chain-method">.${aggMethod}(</span><span class="chain-lambda">${query.aggregationLambda}</span><span class="chain-method">)</span>`);
            } else {
                parts.push(html`<span class="chain-method">.${aggMethod}(...)</span>`);
            }
        } else {
            // Standard queries: Entity.where(...).select(...).sortedBy(...)
            if (hasPredicate) {
                parts.push(html`<span class="chain-method">.where(</span><span class="chain-lambda">${query.predicateLambda}</span><span class="chain-method">)</span>`);
            }
            if (hasProjection) {
                parts.push(html`<span class="chain-method">.select(</span><span class="chain-lambda">${query.projectionLambda}</span><span class="chain-method">)</span>`);
            }
            // Handle sorting
            if (hasSortLambda) {
                const sortMethod = query.sortDescending ? 'sortedDescendingBy' : 'sortedBy';
                parts.push(html`<span class="chain-method">.${sortMethod}(</span><span class="chain-lambda">${query.sortLambda}</span><span class="chain-method">)</span>`);
            }
            // Handle distinct
            if (query.hasDistinct) {
                parts.push(html`<span class="chain-method">.distinct()</span>`);
            }
            // Handle case where neither predicate nor projection nor sort (should not happen but be safe)
            if (!hasPredicate && !hasProjection && !hasSortLambda) {
                parts.push(html`<span class="chain-method">.where(...)</span>`);
            }
        }

        // Handle skip() and limit() - shown after other operations, before terminal
        if (query.skipValue != null) {
            parts.push(html`<span class="chain-method">.skip(${query.skipValue})</span>`);
        }
        if (query.limitValue != null) {
            parts.push(html`<span class="chain-method">.limit(${query.limitValue})</span>`);
        }

        // Add terminal based on query type or use terminalMethodName from backend
        const terminal = this._getTerminalMethod(query, queryType, isCount, isAggregation);
        parts.push(html`<span class="chain-terminal">${terminal}</span>`);

        return html`<div class="method-chain">${parts}</div>`;
    }

    _detectAggregationMethod(jpql) {
        if (jpql.includes('MAX(')) return 'max';
        if (jpql.includes('MIN(')) return 'min';
        if (jpql.includes('AVG(')) return 'avg';
        if (jpql.includes('SUM(')) return 'sum';
        if (jpql.includes('COUNT(')) return 'count';
        return 'aggregate';
    }

    _getAggregationMethod(aggregationType, jpql) {
        // Use aggregationType from backend if available
        if (aggregationType) {
            // Convert UPPER_SNAKE_CASE to camelCase method name
            switch (aggregationType) {
                case 'MIN': return 'min';
                case 'MAX': return 'max';
                case 'AVG': return 'avg';
                case 'SUM_INTEGER': return 'sumInteger';
                case 'SUM_LONG': return 'sumLong';
                case 'SUM_DOUBLE': return 'sumDouble';
                default: return aggregationType.toLowerCase();
            }
        }
        // Fallback to JPQL detection
        return this._detectAggregationMethod(jpql);
    }

    /**
     * Checks if the terminal method name is an aggregation method.
     * Aggregation methods are intermediate operations, not terminal - the actual terminal is getSingleResult().
     */
    _isAggregationTerminal(methodName) {
        const aggregationMethods = ['min', 'max', 'avg', 'sumInteger', 'sumLong', 'sumDouble'];
        return aggregationMethods.includes(methodName);
    }

    _getTerminalMethod(query, queryType, isCount, isAggregation) {
        // Use terminalMethodName from backend if available
        if (query.terminalMethodName) {
            const method = query.terminalMethodName;
            // For aggregation queries, the backend sets terminalMethodName to the aggregation method (min, max, etc.)
            // but the actual terminal operation is getSingleResult()
            if (this._isAggregationTerminal(method)) {
                return '.getSingleResult()';
            }
            // Format method name with parentheses
            if (method === 'toList' || method === 'count' || method === 'exists' ||
                method === 'findFirst' || method === 'getSingleResult') {
                return `.${method}()`;
            }
            return `.${method}()`;
        }
        // Fallback to type-based detection
        if (isCount) return '.count()';
        if (isAggregation) return '.getSingleResult()';
        if (queryType === 'List' || queryType.includes('List')) return '.toList()';
        if (queryType.includes('Projection')) return '.toList()';
        if (queryType.includes('Joined')) return '.toList()';
        return '.toList()';
    }

    _formatJpql(jpql) {
        // Remove leading/trailing whitespace, then add line breaks for readability
        return jpql.trim()
            .replace(/ FROM /g, '\nFROM ')
            .replace(/ WHERE /g, '\nWHERE ')
            .replace(/ AND /g, '\n  AND ')
            .replace(/ OR /g, '\n  OR ')
            .replace(/ ORDER BY /g, '\nORDER BY ')
            .replace(/ GROUP BY /g, '\nGROUP BY ')
            .replace(/ HAVING /g, '\nHAVING ');
    }

    _onRowClick(e) {
        const query = e.detail.value;
        if (query) {
            this._selectedQuery = query;
        }
    }

    _closeJpqlPanel() {
        this._selectedQuery = null;
    }

    _entityRenderer(query) {
        const packageName = query.entityClassName.substring(0, query.entityClassName.lastIndexOf('.'));
        return html`
            <div class="entity-info">
                <span class="entity-name">${query.entityName}</span>
                <span class="entity-package" title="${packageName}">${packageName}</span>
            </div>
        `;
    }

    _queryTypeRenderer(query) {
        const typeClass = this._getQueryTypeClass(query.queryType);
        return html`<span class="query-type ${typeClass}">${query.queryType}</span>`;
    }

    _getQueryTypeClass(queryType) {
        if (queryType.includes('Count')) return 'query-type-count';
        if (queryType.includes('Join') || queryType.includes('Joined')) return 'query-type-join';
        if (queryType.includes('Group')) return 'query-type-group';
        if (queryType.includes('Aggregation')) return 'query-type-aggregation';
        return 'query-type-list';
    }

    _queryIdRenderer(query) {
        // Show full query ID on hover for long IDs
        return html`<div class="query-id-cell" title="${query.queryId}"><code>${this._shortenQueryId(query.queryId)}</code></div>`;
    }

    _shortenQueryId(queryId) {
        // If query ID is very long, show abbreviated version in cell
        if (queryId.length > 60) {
            const parts = queryId.split('#');
            if (parts.length === 2) {
                const classPath = parts[0];
                const method = parts[1];
                // Get last two segments of class path
                const segments = classPath.split('.');
                const shortClass = segments.length > 2
                    ? '...' + segments.slice(-2).join('.')
                    : classPath;
                return shortClass + '#' + method;
            }
        }
        return queryId;
    }

    _generatedClassRenderer(query) {
        // Show full FQN on hover, abbreviated in cell for very long class names
        const fullName = query.generatedClassName;
        const displayName = this._shortenClassName(fullName);
        return html`<span class="generated-class" title="${fullName}">${displayName}</span>`;
    }

    _shortenClassName(className) {
        // Show abbreviated class name for very long packages
        if (className.length > 50) {
            const lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                const packagePart = className.substring(0, lastDot);
                const simpleName = className.substring(lastDot + 1);
                // Abbreviate package to first letters
                const shortPackage = packagePart.split('.')
                    .map(p => p.charAt(0))
                    .join('.') + '.';
                return shortPackage + simpleName;
            }
        }
        return className;
    }

    _capturedVarsRenderer(query) {
        return html`<span class="captured-badge">${query.capturedVariables}</span>`;
    }

    _matchesTerm(query, searchTerm) {
        if (!searchTerm) return true;
        return (
            query.entityName.toLowerCase().includes(searchTerm) ||
            query.entityClassName.toLowerCase().includes(searchTerm) ||
            query.queryId.toLowerCase().includes(searchTerm) ||
            query.generatedClassName.toLowerCase().includes(searchTerm) ||
            query.queryType.toLowerCase().includes(searchTerm) ||
            (query.jpql && query.jpql.toLowerCase().includes(searchTerm))
        );
    }
}

customElements.define('qwc-qubit-queries', QwcQubitQueries);
