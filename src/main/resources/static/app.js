const form = document.querySelector('#chatForm');
const input = document.querySelector('#messageInput');
const messages = document.querySelector('#messages');
const suggestions = document.querySelector('#suggestions');
const sendButton = document.querySelector('#sendButton');
const stopButton = document.querySelector('#stopButton');
const clearButton = document.querySelector('#clearButton');
const connection = document.querySelector('.connection');
const connectionText = document.querySelector('#connectionText');
const historyButton = document.querySelector('#historyButton');
const historyPanel = document.querySelector('#historyPanel');
const historyList = document.querySelector('#historyList');
const newConversationButton = document.querySelector('#newConversationButton');
const renameDialog = document.querySelector('#renameDialog');
const renameForm = document.querySelector('#renameForm');
const renameInput = document.querySelector('#renameInput');
const renameError = document.querySelector('#renameError');
const cancelRenameButton = document.querySelector('#cancelRenameButton');
const deleteDialog = document.querySelector('#deleteDialog');
const deleteConversationTitle = document.querySelector('#deleteConversationTitle');
const deleteError = document.querySelector('#deleteError');
const cancelDeleteButton = document.querySelector('#cancelDeleteButton');
const confirmDeleteButton = document.querySelector('#confirmDeleteButton');
const modelSelect = document.querySelector('#modelSelect');
const benchmarkForm = document.querySelector('#benchmarkForm');
const benchmarkModel = document.querySelector('#benchmarkModel');
const benchmarkPrompt = document.querySelector('#benchmarkPrompt');
const benchmarkIteration = document.querySelector('#benchmarkIteration');
const benchmarkRun = document.querySelector('#benchmarkRun');
const benchmarkStatus = document.querySelector('#benchmarkStatus');
const benchmarkResults = document.querySelector('#benchmarkResults');
const benchmarkSummary = document.querySelector('#benchmarkSummary');
const benchmarkMethodology = document.querySelector('#benchmarkMethodology');
const benchmarkAnswerDialog = document.querySelector('#benchmarkAnswerDialog');
const benchmarkAnswerTitle = document.querySelector('#benchmarkAnswerTitle');
const benchmarkAnswerContent = document.querySelector('#benchmarkAnswerContent');
const closeBenchmarkAnswer = document.querySelector('#closeBenchmarkAnswer');

let activeRequest = null;
let activeConversationId = null;
let managedConversationId = null;

function setStatus(status, label) {
    connection.classList.toggle('busy', status === 'busy');
    connection.classList.toggle('error', status === 'error');
    connectionText.textContent = label;
}

function setBusy(busy) {
    sendButton.disabled = busy;
    input.disabled = busy;
    modelSelect.disabled = busy;
    stopButton.hidden = !busy;
    suggestions.hidden = busy;
    if (!busy) input.focus();
}

function appendInlineMarkdown(parent, text) {
    const pattern = /(`[^`\n]+`|\*\*[^*\n]+\*\*|\*[^*\n]+\*|\[[^\]\n]+\]\(https?:\/\/[^)\s]+\))/g;
    let cursor = 0;

    for (const match of text.matchAll(pattern)) {
        parent.append(document.createTextNode(text.slice(cursor, match.index)));
        const token = match[0];

        if (token.startsWith('`')) {
            const code = document.createElement('code');
            code.textContent = token.slice(1, -1);
            parent.append(code);
        } else if (token.startsWith('**')) {
            const strong = document.createElement('strong');
            strong.textContent = token.slice(2, -2);
            parent.append(strong);
        } else if (token.startsWith('*')) {
            const emphasis = document.createElement('em');
            emphasis.textContent = token.slice(1, -1);
            parent.append(emphasis);
        } else {
            const parts = token.match(/^\[([^\]]+)]\((https?:\/\/[^)]+)\)$/);
            const link = document.createElement('a');
            link.textContent = parts[1];
            link.href = parts[2];
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            parent.append(link);
        }
        cursor = match.index + token.length;
    }

    parent.append(document.createTextNode(text.slice(cursor)));
}

function createCodeBlock(language, codeText) {
    const wrapper = document.createElement('div');
    wrapper.className = 'code-block';

    const header = document.createElement('div');
    header.className = 'code-header';

    const label = document.createElement('span');
    label.textContent = language || 'código';

    const copy = document.createElement('button');
    copy.type = 'button';
    copy.className = 'copy-code';
    copy.textContent = 'Copiar';
    copy.setAttribute('aria-label', 'Copiar bloco de código');

    const pre = document.createElement('pre');
    const code = document.createElement('code');
    code.textContent = codeText.replace(/\n$/, '');
    pre.append(code);
    header.append(label, copy);
    wrapper.append(header, pre);
    return wrapper;
}

function renderMarkdown(target, markdown) {
    target.replaceChildren();
    const lines = markdown.replace(/\r\n/g, '\n').split('\n');
    let paragraph = [];
    let list = null;
    let listType = null;
    let inCode = false;
    let codeLanguage = '';
    let codeLines = [];

    const flushParagraph = () => {
        if (!paragraph.length) return;
        const element = document.createElement('p');
        appendInlineMarkdown(element, paragraph.join('\n'));
        target.append(element);
        paragraph = [];
    };

    const flushList = () => {
        if (list) target.append(list);
        list = null;
        listType = null;
    };

    const flushCode = () => {
        target.append(createCodeBlock(codeLanguage, codeLines.join('\n')));
        codeLines = [];
        codeLanguage = '';
    };

    for (const line of lines) {
        const fence = line.match(/^```([\w#+.-]*)\s*$/);
        if (fence) {
            if (inCode) {
                flushCode();
                inCode = false;
            } else {
                flushParagraph();
                flushList();
                inCode = true;
                codeLanguage = fence[1];
            }
            continue;
        }

        if (inCode) {
            codeLines.push(line);
            continue;
        }

        const heading = line.match(/^(#{1,3})\s+(.+)$/);
        const unordered = line.match(/^[-*]\s+(.+)$/);
        const ordered = line.match(/^\d+\.\s+(.+)$/);
        const quote = line.match(/^>\s?(.*)$/);

        if (heading) {
            flushParagraph();
            flushList();
            const element = document.createElement(`h${heading[1].length + 2}`);
            appendInlineMarkdown(element, heading[2]);
            target.append(element);
        } else if (unordered || ordered) {
            flushParagraph();
            const nextType = unordered ? 'ul' : 'ol';
            if (listType !== nextType) {
                flushList();
                listType = nextType;
                list = document.createElement(nextType);
            }
            const item = document.createElement('li');
            appendInlineMarkdown(item, (unordered || ordered)[1]);
            list.append(item);
        } else if (quote) {
            flushParagraph();
            flushList();
            const element = document.createElement('blockquote');
            appendInlineMarkdown(element, quote[1]);
            target.append(element);
        } else if (!line.trim()) {
            flushParagraph();
            flushList();
        } else {
            flushList();
            paragraph.push(line);
        }
    }

    flushParagraph();
    flushList();
    if (inCode) flushCode();
}

function addMessage(role, text = '') {
    const article = document.createElement('article');
    article.className = `message ${role}`;

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.setAttribute('aria-hidden', 'true');
    avatar.textContent = role === 'user' ? 'EU' : 'A';

    const bubble = document.createElement('div');
    bubble.className = 'bubble';

    const author = document.createElement('span');
    author.className = 'message-author';
    author.textContent = role === 'user' ? 'VOCÊ' : 'ATLAS';

    const content = document.createElement('div');
    content.className = 'message-content';
    if (role === 'assistant') renderMarkdown(content, text);
    else content.textContent = text;

    bubble.append(author, content);
    article.append(avatar, bubble);
    messages.append(article);
    messages.scrollTop = messages.scrollHeight;
    return content;
}

function formatDuration(milliseconds) {
    if (milliseconds < 1000) return `${milliseconds} ms`;
    return `${(milliseconds / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} s`;
}

function renderMetrics(answer, metrics) {
    answer.parentElement.querySelector('.inference-metrics')?.remove();
    if (!metrics) return;

    const panel = document.createElement('div');
    panel.className = 'inference-metrics';
    panel.setAttribute('aria-label', 'Métricas da inferência');

    const entries = [];
    if (metrics.timeToFirstTokenMs !== null) {
        entries.push(['1º token', formatDuration(metrics.timeToFirstTokenMs)]);
    }
    entries.push(['Total', formatDuration(metrics.totalDurationMs)]);
    if (metrics.tokensPerSecond !== null) {
        entries.push(['Velocidade', `${metrics.tokensPerSecond.toLocaleString('pt-BR')} tok/s`]);
    }
    if (metrics.promptTokens !== null) entries.push(['Entrada', `${metrics.promptTokens} tokens`]);
    if (metrics.completionTokens !== null) entries.push(['Saída', `${metrics.completionTokens} tokens`]);
    const messageUnit = metrics.contextMessages === 1 ? 'msg' : 'msgs';
    entries.push(['Contexto', `${metrics.contextMessages} ${messageUnit} · ${metrics.contextCharacters} chars`]);

    for (const [label, value] of entries) {
        const metric = document.createElement('span');
        const name = document.createElement('small');
        name.textContent = label;
        const result = document.createElement('strong');
        result.textContent = value;
        metric.append(name, result);
        panel.append(metric);
    }
    answer.parentElement.append(panel);
}

function parseEvent(block) {
    let event = 'message';
    const data = [];
    for (const line of block.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
    }
    return { event, data: data.join('\n') };
}

async function sendMessage(message) {
    addMessage('user', message);
    const answer = addMessage('assistant');
    answer.classList.add('cursor');
    setBusy(true);
    setStatus('busy', 'Gerando resposta');

    activeRequest = new AbortController();
    let completed = false;
    let answerText = '';
    let renderPending = false;

    const refreshAnswer = () => {
        if (renderPending) return;
        renderPending = true;
        requestAnimationFrame(() => {
            renderMarkdown(answer, answerText);
            messages.scrollTop = messages.scrollHeight;
            renderPending = false;
        });
    };

    try {
        const response = await fetch('/api/ai/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                message,
                conversationId: activeConversationId,
                model: modelSelect.value
            }),
            signal: activeRequest.signal
        });

        if (!response.ok) {
            const problem = await response.json().catch(() => ({}));
            throw new Error(problem.detail || `Falha HTTP ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { value, done } = await reader.read();
            buffer += decoder.decode(value || new Uint8Array(), { stream: !done });

            const blocks = buffer.split(/\r?\n\r?\n/);
            buffer = blocks.pop() || '';

            for (const block of blocks) {
                if (!block.trim()) continue;
                const parsed = parseEvent(block);
                const payload = JSON.parse(parsed.data);

                if (parsed.event === 'token') {
                    answerText += payload.content || '';
                    refreshAnswer();
                } else if (parsed.event === 'conversation') {
                    activeConversationId = payload.conversationId;
                } else if (parsed.event === 'done') {
                    completed = true;
                    renderMetrics(answer, payload.metrics);
                } else if (parsed.event === 'error') {
                    throw new Error(payload.message || 'O streaming foi interrompido.');
                }
            }

            if (done) break;
        }

        renderMarkdown(answer, answerText);
        await loadConversations();
        setStatus('', completed ? 'Resposta concluída' : 'Fluxo encerrado');
    } catch (error) {
        if (error.name === 'AbortError') {
            if (!answerText) answerText = 'Resposta interrompida.';
            renderMarkdown(answer, answerText);
            setStatus('', 'Interrompido');
        } else {
            answerText = answerText || `Não consegui responder: ${error.message}`;
            renderMarkdown(answer, answerText);
            setStatus('error', 'Falha na conexão');
        }
    } finally {
        answer.classList.remove('cursor');
        activeRequest = null;
        setBusy(false);
    }
}

form.addEventListener('submit', event => {
    event.preventDefault();
    const message = input.value.trim();
    if (!message || activeRequest) return;
    input.value = '';
    input.style.height = 'auto';
    sendMessage(message);
});

input.addEventListener('input', () => {
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 150)}px`;
});

input.addEventListener('keydown', event => {
    if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
        event.preventDefault();
        form.requestSubmit();
    }
});

suggestions.addEventListener('click', event => {
    const button = event.target.closest('[data-prompt]');
    if (!button || activeRequest) return;
    input.value = button.dataset.prompt;
    form.requestSubmit();
});

messages.addEventListener('click', async event => {
    const button = event.target.closest('.copy-code');
    if (!button) return;
    const code = button.closest('.code-block').querySelector('code').textContent;
    try {
        await navigator.clipboard.writeText(code);
        button.textContent = 'Copiado!';
        setTimeout(() => { button.textContent = 'Copiar'; }, 1600);
    } catch {
        button.textContent = 'Não foi possível copiar';
        setTimeout(() => { button.textContent = 'Copiar'; }, 2000);
    }
});

stopButton.addEventListener('click', () => activeRequest?.abort());

clearButton.addEventListener('click', () => {
    if (activeRequest) activeRequest.abort();
    messages.replaceChildren();
    addMessage('assistant', 'Conversa limpa. Em que posso ajudar agora?');
    setStatus('', 'Pronto');
});

function resetConversation() {
    activeConversationId = null;
    messages.replaceChildren();
    addMessage('assistant', 'Nova conversa iniciada. O que vamos explorar?');
    historyPanel.hidden = true;
    setStatus('', 'Pronto');
}

async function loadConversations() {
    try {
        const response = await fetch('/api/conversations');
        if (!response.ok) return;
        const conversations = await response.json();
        historyList.replaceChildren();
        if (!conversations.length) {
            const empty = document.createElement('p');
            empty.className = 'history-empty';
            empty.textContent = 'Nenhuma conversa salva ainda.';
            historyList.append(empty);
            return;
        }
        for (const conversation of conversations) {
            const item = document.createElement('div');
            item.className = 'history-item';
            item.classList.toggle('active', conversation.id === activeConversationId);

            const open = document.createElement('button');
            open.type = 'button';
            open.className = 'history-open';
            open.dataset.action = 'open';
            open.dataset.id = conversation.id;
            const title = document.createElement('strong');
            title.textContent = conversation.title;
            const date = document.createElement('small');
            date.textContent = new Date(conversation.updatedAt).toLocaleString('pt-BR');
            open.append(title, date);

            const actions = document.createElement('div');
            actions.className = 'history-actions';
            const rename = document.createElement('button');
            rename.type = 'button';
            rename.dataset.action = 'rename';
            rename.dataset.id = conversation.id;
            rename.dataset.title = conversation.title;
            rename.textContent = 'Renomear';
            rename.setAttribute('aria-label', `Renomear ${conversation.title}`);
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'danger-action';
            remove.dataset.action = 'delete';
            remove.dataset.id = conversation.id;
            remove.dataset.title = conversation.title;
            remove.textContent = 'Excluir';
            remove.setAttribute('aria-label', `Excluir ${conversation.title}`);
            actions.append(rename, remove);
            item.append(open, actions);
            historyList.append(item);
        }
    } catch {
        historyList.textContent = 'Histórico indisponível.';
    }
}

async function openConversation(id) {
    const response = await fetch(`/api/conversations/${encodeURIComponent(id)}`);
    if (!response.ok) return;
    const conversation = await response.json();
    activeConversationId = conversation.id;
    messages.replaceChildren();
    for (const message of conversation.messages) {
        addMessage(message.role === 'USER' ? 'user' : 'assistant', message.content);
    }
    historyPanel.hidden = true;
    setStatus('', 'Conversa carregada');
}

historyButton.addEventListener('click', async () => {
    historyPanel.hidden = !historyPanel.hidden;
    if (!historyPanel.hidden) await loadConversations();
});

function showRenameDialog(id, title) {
    managedConversationId = id;
    renameInput.value = title;
    renameError.hidden = true;
    renameDialog.showModal();
    renameInput.select();
}

function showDeleteDialog(id, title) {
    managedConversationId = id;
    deleteConversationTitle.textContent = `“${title}”`;
    deleteError.hidden = true;
    deleteDialog.showModal();
}

historyList.addEventListener('click', event => {
    const action = event.target.closest('[data-action]');
    if (!action || activeRequest) return;
    if (action.dataset.action === 'open') openConversation(action.dataset.id);
    if (action.dataset.action === 'rename') showRenameDialog(action.dataset.id, action.dataset.title);
    if (action.dataset.action === 'delete') showDeleteDialog(action.dataset.id, action.dataset.title);
});

renameForm.addEventListener('submit', async event => {
    event.preventDefault();
    const title = renameInput.value.trim();
    if (!title || !managedConversationId) return;
    renameError.hidden = true;
    try {
        const response = await fetch(`/api/conversations/${encodeURIComponent(managedConversationId)}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title })
        });
        if (!response.ok) {
            const problem = await response.json().catch(() => ({}));
            throw new Error(problem.detail || `Falha HTTP ${response.status}`);
        }
        renameDialog.close();
        managedConversationId = null;
        await loadConversations();
        setStatus('', 'Conversa renomeada');
    } catch (error) {
        renameError.textContent = error.message;
        renameError.hidden = false;
    }
});

confirmDeleteButton.addEventListener('click', async () => {
    if (!managedConversationId) return;
    deleteError.hidden = true;
    confirmDeleteButton.disabled = true;
    const deletedId = managedConversationId;
    try {
        const response = await fetch(`/api/conversations/${encodeURIComponent(deletedId)}`, {
            method: 'DELETE'
        });
        if (!response.ok) {
            const problem = await response.json().catch(() => ({}));
            throw new Error(problem.detail || `Falha HTTP ${response.status}`);
        }
        deleteDialog.close();
        managedConversationId = null;
        if (activeConversationId === deletedId) resetConversation();
        await loadConversations();
        setStatus('', 'Conversa excluída');
    } catch (error) {
        deleteError.textContent = error.message;
        deleteError.hidden = false;
    } finally {
        confirmDeleteButton.disabled = false;
    }
});

cancelRenameButton.addEventListener('click', () => {
    renameDialog.close();
    managedConversationId = null;
});

cancelDeleteButton.addEventListener('click', () => {
    deleteDialog.close();
    managedConversationId = null;
});

newConversationButton.addEventListener('click', resetConversation);

async function loadModels() {
    try {
        const response = await fetch('/api/ai/models');
        if (!response.ok) throw new Error();
        const catalog = await response.json();
        modelSelect.replaceChildren();
        benchmarkModel.replaceChildren();
        for (const model of catalog.models) {
            for (const select of [modelSelect, benchmarkModel]) {
                const option = document.createElement('option');
                option.value = model;
                option.textContent = model;
                option.selected = model === catalog.defaultModel;
                select.append(option);
            }
        }
    } catch {
        setStatus('error', 'Modelos indisponíveis');
    }
}

async function loadBenchmarkPrompts() {
    const response = await fetch('/api/benchmarks/prompts');
    if (!response.ok) throw new Error('Não foi possível carregar os prompts.');
    benchmarkPrompt.replaceChildren();
    for (const prompt of await response.json()) {
        const option = document.createElement('option');
        option.value = prompt.id;
        option.textContent = prompt.title;
        option.title = prompt.text;
        benchmarkPrompt.append(option);
    }
}

async function loadBenchmarkResults() {
    const response = await fetch('/api/benchmarks/results');
    if (!response.ok) return;
    benchmarkResults.replaceChildren();
    for (const result of await response.json()) {
        const row = document.createElement('tr');
        const values = [result.model, result.promptTitle,
            result.coldStart ? '1 · fria' : `${result.iteration} · aquecida`,
            result.timeToFirstTokenMs === null ? '—' : formatDuration(result.timeToFirstTokenMs),
            formatDuration(result.totalDurationMs),
            result.tokensPerSecond === null ? '—' : `${result.tokensPerSecond.toLocaleString('pt-BR')} tok/s`];
        for (const value of values) {
            const cell = document.createElement('td');
            cell.textContent = value;
            row.append(cell);
        }
        const answerCell = document.createElement('td');
        const answerButton = document.createElement('button');
        answerButton.type = 'button';
        answerButton.className = 'benchmark-answer';
        answerButton.textContent = 'Ver';
        answerButton.dataset.answer = result.answer;
        answerButton.dataset.title = `${result.model} · ${result.promptTitle} · rodada ${result.iteration}`;
        answerCell.append(answerButton);
        row.append(answerCell);
        const qualityCell = document.createElement('td');
        const quality = document.createElement('select');
        quality.className = 'quality-select';
        quality.dataset.id = result.id;
        quality.innerHTML = '<option value="">—</option><option>1</option><option>2</option><option>3</option><option>4</option><option>5</option>';
        quality.value = result.qualityScore || '';
        quality.title = result.evaluationNotes || 'Selecione uma nota de 1 a 5';
        qualityCell.append(quality);
        row.append(qualityCell);
        benchmarkResults.append(row);
    }
}

function summaryValue(value, formatter) {
    return value === null ? '—' : formatter(value);
}

async function loadBenchmarkSummary() {
    const response = await fetch('/api/benchmarks/summary');
    if (!response.ok) return;
    const summary = await response.json();
    benchmarkSummary.replaceChildren();
    benchmarkMethodology.textContent = summary.methodology;
    for (const model of summary.models) {
        const card = document.createElement('article');
        card.className = `benchmark-model-card${model.recommended ? ' recommended' : ''}`;
        if (model.recommended) {
            const badge = document.createElement('span');
            badge.className = 'benchmark-badge';
            badge.textContent = 'MELHOR EQUILÍBRIO';
            card.append(badge);
        }
        const title = document.createElement('h3');
        title.textContent = model.model;
        const metrics = document.createElement('dl');
        const entries = [
            ['Índice', `${model.balanceScore.toLocaleString('pt-BR')} / 100`],
            ['Qualidade', summaryValue(model.averageQuality, value => `${value.toLocaleString('pt-BR')} / 5`)],
            ['Velocidade', summaryValue(model.averageTokensPerSecond, value => `${value.toLocaleString('pt-BR')} tok/s`)],
            ['1º token', summaryValue(model.averageTimeToFirstTokenMs, formatDuration)],
            ['Fria', summaryValue(model.averageColdDurationMs, formatDuration)],
            ['Aquecida', summaryValue(model.averageWarmDurationMs, formatDuration)],
            ['Cobertura', `${model.validRuns}/18 · ${model.coveragePercent.toLocaleString('pt-BR')}%`]
        ];
        for (const [name, value] of entries) {
            const term = document.createElement('dt'); term.textContent = name;
            const detail = document.createElement('dd'); detail.textContent = value;
            metrics.append(term, detail);
        }
        card.append(title, metrics);
        benchmarkSummary.append(card);
    }
}

benchmarkForm.addEventListener('submit', async event => {
    event.preventDefault();
    benchmarkRun.disabled = true;
    benchmarkStatus.textContent = 'Executando benchmark — aguarde a resposta completa…';
    try {
        const response = await fetch('/api/benchmarks/runs', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ model: benchmarkModel.value, promptId: benchmarkPrompt.value,
                iteration: Number(benchmarkIteration.value) })
        });
        if (!response.ok) {
            const problem = await response.json().catch(() => ({}));
            throw new Error(problem.detail || `Falha HTTP ${response.status}`);
        }
        await Promise.all([loadBenchmarkResults(), loadBenchmarkSummary()]);
        benchmarkStatus.textContent = 'Rodada concluída e armazenada.';
    } catch (error) {
        benchmarkStatus.textContent = `Falha: ${error.message}`;
    } finally {
        benchmarkRun.disabled = false;
    }
});

benchmarkResults.addEventListener('click', event => {
    const button = event.target.closest('.benchmark-answer');
    if (!button) return;
    benchmarkAnswerTitle.textContent = button.dataset.title;
    renderMarkdown(benchmarkAnswerContent, button.dataset.answer);
    benchmarkAnswerDialog.showModal();
});

closeBenchmarkAnswer.addEventListener('click', () => benchmarkAnswerDialog.close());

benchmarkResults.addEventListener('change', async event => {
    const select = event.target.closest('.quality-select');
    if (!select || !select.value) return;
    const previous = select.title === 'Selecione uma nota de 1 a 5' ? '' : select.title;
    const notes = window.prompt('Observação opcional sobre a qualidade desta resposta:', previous);
    const response = await fetch(`/api/benchmarks/results/${encodeURIComponent(select.dataset.id)}/evaluation`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ qualityScore: Number(select.value), notes })
    });
    benchmarkStatus.textContent = response.ok ? 'Avaliação salva.' : 'Não foi possível salvar a avaliação.';
    if (response.ok) await Promise.all([loadBenchmarkResults(), loadBenchmarkSummary()]);
});

Promise.all([loadModels(), loadBenchmarkPrompts(), loadBenchmarkResults(), loadBenchmarkSummary()])
    .catch(error => setStatus('error', error.message || 'Falha ao carregar'))
    .finally(() => input.focus());
