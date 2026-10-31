const form = document.querySelector('#chatForm');
const input = document.querySelector('#messageInput');
const messages = document.querySelector('#messages');
const suggestions = document.querySelector('#suggestions');
const sendButton = document.querySelector('#sendButton');
const stopButton = document.querySelector('#stopButton');
const clearButton = document.querySelector('#clearButton');
const connection = document.querySelector('.connection');
const connectionText = document.querySelector('#connectionText');

let activeRequest = null;

function setStatus(status, label) {
    connection.classList.toggle('busy', status === 'busy');
    connection.classList.toggle('error', status === 'error');
    connectionText.textContent = label;
}

function setBusy(busy) {
    sendButton.disabled = busy;
    input.disabled = busy;
    stopButton.hidden = !busy;
    suggestions.hidden = busy;
    if (!busy) input.focus();
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

    const paragraph = document.createElement('p');
    paragraph.textContent = text;

    bubble.append(author, paragraph);
    article.append(avatar, bubble);
    messages.append(article);
    messages.scrollTop = messages.scrollHeight;
    return paragraph;
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

    try {
        const response = await fetch('/api/ai/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message }),
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
                    answer.textContent += payload.content || '';
                    messages.scrollTop = messages.scrollHeight;
                } else if (parsed.event === 'done') {
                    completed = true;
                } else if (parsed.event === 'error') {
                    throw new Error(payload.message || 'O streaming foi interrompido.');
                }
            }

            if (done) break;
        }

        setStatus('', completed ? 'Resposta concluída' : 'Fluxo encerrado');
    } catch (error) {
        if (error.name === 'AbortError') {
            if (!answer.textContent) answer.textContent = 'Resposta interrompida.';
            setStatus('', 'Interrompido');
        } else {
            answer.textContent = answer.textContent || `Não consegui responder: ${error.message}`;
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

stopButton.addEventListener('click', () => activeRequest?.abort());

clearButton.addEventListener('click', () => {
    if (activeRequest) activeRequest.abort();
    messages.replaceChildren();
    addMessage('assistant', 'Conversa limpa. Em que posso ajudar agora?');
    setStatus('', 'Pronto');
});

input.focus();
