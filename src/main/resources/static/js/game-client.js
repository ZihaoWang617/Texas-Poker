/**
 * WePoker 客户端（自动流程版，REST 轮询）
 */

const gameState = {
    connected: false,
    playerId: null,
    nickname: null,
    tableId: null,
    myStack: 0,
    mySeat: -1,
    myStatus: 'SITTING',
    gameStatus: 'WAITING',
    potAmount: 0,
    potBreakdown: [],
    currentBetThisStreet: 0,
    players: [],
    communityCards: [],
    currentPlayerToActSeat: -1,
    actionDeadline: 0,
    buttonSeat: -1,
    smallBlindSeat: -1,
    bigBlindSeat: -1,
    smallBlindAmount: 500,
    bigBlindAmount: 1000,
    myHoleCards: [],
    revealedHoleCards: {},
    myToCall: 0,
    minRaise: 0,
    recentAction: '-'
};

let pollTimer = null;
let lastUiDebugAt = 0;

function initializeApp() {
    const urlParams = new URLSearchParams(window.location.search);
    let tableIdFromUrl = urlParams.get('table');
    if (!tableIdFromUrl) {
        tableIdFromUrl = Math.floor(Math.random() * 1000000).toString();
    }

    gameState.tableId = parseInt(tableIdFromUrl, 10);

    if (!sessionStorage.getItem('playerId')) {
        sessionStorage.setItem('playerId', String(Math.floor(Math.random() * 1000000000)));
    }
    gameState.playerId = sessionStorage.getItem('playerId');

    const baseUrl = window.location.origin + window.location.pathname;
    const roomUrl = `${baseUrl}?table=${gameState.tableId}`;
    document.getElementById('shareLink').value = roomUrl;
    updateShareLinkForLan();
    document.getElementById('tableId').textContent = gameState.tableId;

    const joinModal = new bootstrap.Modal(document.getElementById('joinModal'));
    joinModal.show();

    setInterval(updateUI, 250);
}

async function updateShareLinkForLan() {
    try {
        const host = window.location.hostname;
        if (host !== 'localhost' && host !== '127.0.0.1') {
            return;
        }

        const res = await fetch('/api/game/network-info');
        const result = await res.json();
        if (result.code !== 200 || !result.data || !result.data.lanIp) {
            return;
        }

        const lanBase = `${window.location.protocol}//${result.data.lanIp}:${window.location.port || result.data.serverPort}${window.location.pathname}`;
        document.getElementById('shareLink').value = `${lanBase}?table=${gameState.tableId}`;
    } catch (e) {
        console.warn('unable to resolve LAN share link', e);
    }
}

async function joinTable() {
    const nickname = document.getElementById('inputNickname').value.trim();
    const buyIn = parseInt(document.getElementById('inputBuyIn').value, 10);

    if (!nickname || nickname.length < 2 || nickname.length > 20) {
        alert('昵称长度必须在 2-20 个字符之间');
        return;
    }
    if (!buyIn || buyIn < 50 || buyIn > 5000) {
        alert('买入金额必须在 ¥50-¥5000 之间');
        return;
    }

    gameState.nickname = nickname;
    gameState.myStack = buyIn * 100;

    try {
        const res = await fetch(`/api/game/tables/${gameState.tableId}/join`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                tableId: gameState.tableId,
                playerId: gameState.playerId,
                nickname: gameState.nickname,
                buyIn: gameState.myStack
            })
        });

        const data = await res.json();
        if (data.code !== 200) {
            alert(data.message || '加入房间失败');
            return;
        }

        bootstrap.Modal.getInstance(document.getElementById('joinModal')).hide();
        gameState.connected = true;
        updateStatus('已连接', 'success');
        document.getElementById('playerName').textContent = `玩家: ${gameState.nickname}`;

        await refreshTableState();
        startPolling();
    } catch (err) {
        console.error(err);
        updateStatus('连接失败', 'danger');
        alert('加入房间失败，请确认后端已启动');
    }
}

function startPolling() {
    if (pollTimer) {
        clearInterval(pollTimer);
    }
    pollTimer = setInterval(refreshTableState, 900);
}

async function refreshTableState() {
    try {
        const res = await fetch(`/api/game/tables/${gameState.tableId}/state?playerId=${encodeURIComponent(gameState.playerId)}`);
        const data = await res.json();
        if (data.code !== 200 || !data.data) {
            return;
        }

        const table = data.data;
        gameState.gameStatus = table.state || 'WAITING';
        gameState.potAmount = table.totalPotSize || 0;
        gameState.potBreakdown = Array.isArray(table.potBreakdown) ? table.potBreakdown : [];
        gameState.currentBetThisStreet = table.currentBetThisStreet || 0;
        gameState.currentPlayerToActSeat = table.nextToActSeat ?? -1;
        gameState.actionDeadline = table.actionDeadline || 0;
        gameState.buttonSeat = table.buttonSeat ?? -1;
        gameState.smallBlindSeat = table.smallBlindSeat ?? -1;
        gameState.bigBlindSeat = table.bigBlindSeat ?? -1;
        gameState.smallBlindAmount = table.smallBlindAmount || 500;
        gameState.bigBlindAmount = table.bigBlindAmount || 1000;

        const players = Object.values(table.players || {});
        gameState.players = players.map((p) => ({
            playerId: String(p.playerId),
            nickname: p.nickname || '玩家',
            stack: p.stackSize || 0,
            seat: p.seatNumber ?? -1,
            status: p.status || 'SITTING',
            currentBet: p.currentBet || 0,
            isDealer: !!p.isDealer,
            isSmallBlind: !!p.isSmallBlind,
            isBigBlind: !!p.isBigBlind,
            lastAction: p.lastAction || null
        }));

        const me = gameState.players.find((p) => p.playerId === String(gameState.playerId));
        if (me) {
            gameState.myStack = me.stack;
            gameState.mySeat = me.seat;
            gameState.myStatus = me.status;
            gameState.myToCall = Math.max(0, gameState.currentBetThisStreet - (me.currentBet || 0));
        }

        const hand = table.currentHand || null;
        gameState.communityCards = extractCommunityCards(hand);
        gameState.revealedHoleCards = hand && hand.playerHoleCards ? hand.playerHoleCards : {};
        gameState.myHoleCards = extractMyHoleCards(hand, gameState.playerId);

        gameState.recentAction = buildRecentAction();
        console.log('[state:update]', {
            phase: gameState.gameStatus,
            currentSeat: gameState.currentPlayerToActSeat,
            buttonSeat: gameState.buttonSeat,
            sb: gameState.smallBlindSeat,
            bb: gameState.bigBlindSeat,
            pot: gameState.potAmount,
            players: gameState.players.map(p => ({ seat: p.seat, id: p.playerId, status: p.status, bet: p.currentBet }))
        });
        updateStatus('已连接', 'success');
    } catch (err) {
        console.error('refreshTableState failed', err);
        updateStatus('连接异常', 'danger');
    }
}

function buildRecentAction() {
    let latest = null;
    gameState.players.forEach((p) => {
        if (p.lastAction && (!latest || p.lastAction.timestamp > latest.timestamp)) {
            latest = { ...p.lastAction, nickname: p.nickname };
        }
    });
    if (!latest) return '-';

    const amount = latest.betAmount ? ` ¥${toYuan(latest.betAmount)}` : '';
    return `${latest.nickname}: ${latest.action}${amount}`;
}

async function sendAction(action, amount = 0) {
    try {
        console.log('[action:send]', {
            tableId: gameState.tableId,
            playerId: gameState.playerId,
            action,
            amount,
            phase: gameState.gameStatus,
            currentSeat: gameState.currentPlayerToActSeat,
            mySeat: gameState.mySeat
        });
        const res = await fetch(`/api/game/tables/${gameState.tableId}/action`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                playerId: gameState.playerId,
                action,
                amount
            })
        });
        const data = await res.json();
        console.log('[action:resp]', data);
        if (data.code !== 200) {
            alert(data.message || '操作失败');
            return false;
        }
        await refreshTableState();
        return true;
    } catch (err) {
        console.error(err);
        alert('操作失败，请稍后重试');
        return false;
    }
}

async function playerCheckOrCall() {
    if (gameState.myToCall > 0) {
        await sendAction('CALL', 0);
    } else {
        await sendAction('CHECK', 0);
    }
}

async function playerFold() {
    await sendAction('FOLD', 0);
}

async function allIn() {
    await sendAction('ALL_IN', 0);
}

function showRaiseTools() {
    const tools = document.getElementById('raiseTools');
    const submit = document.getElementById('raiseSubmit');
    const slider = document.getElementById('raiseSlider');
    const raiseAmount = document.getElementById('raiseAmountInline');

    const me = gameState.players.find((p) => p.playerId === String(gameState.playerId));
    const stack = me ? me.stack : 0;
    const minRaise = getMinRaiseTotal(stack);

    gameState.minRaise = minRaise;
    slider.min = minRaise;
    slider.max = stack;
    slider.step = 100;
    slider.value = minRaise;
    raiseAmount.value = (minRaise / 100).toFixed(2);

    console.log('[raise:open]', {
        effectivePot: calculateEffectivePot(),
        currentBet: gameState.currentBetThisStreet,
        bigBlind: gameState.bigBlindAmount,
        minRaise,
        stack
    });

    tools.style.display = 'flex';
    submit.style.display = 'grid';
    updateRaiseQuickButtons();
}

function setRaisePotFraction(fraction) {
    const target = getRaiseAmountByPotFraction(fraction);
    console.log('[raise:preset]', {
        fraction,
        effectivePot: calculateEffectivePot(),
        result: target
    });
    setRaiseAmount(target);
}

function calculateCurrentRoundTotalBets() {
    return gameState.players.reduce((sum, p) => sum + (p.currentBet || 0), 0);
}

function calculateEffectivePot() {
    const currentRoundTotalBets = calculateCurrentRoundTotalBets();
    const potWithoutCurrentRound = Math.max(0, gameState.potAmount - currentRoundTotalBets);
    return potWithoutCurrentRound + currentRoundTotalBets;
}

function getMinRaiseTotal(stack) {
    const stackCap = Number.isFinite(stack) ? stack : (gameState.players.find((p) => p.playerId === String(gameState.playerId))?.stack || 0);
    const minRaiseByState = gameState.currentBetThisStreet > 0
        ? Math.max(gameState.currentBetThisStreet * 2, gameState.currentBetThisStreet + 100)
        : Math.max(gameState.bigBlindAmount, 100);
    return Math.min(Math.max(100, minRaiseByState), stackCap);
}

function getRaiseAmountByPotFraction(fraction) {
    const me = gameState.players.find((p) => p.playerId === String(gameState.playerId));
    const stack = me ? me.stack : 0;
    const effectivePot = calculateEffectivePot();
    const raw = Math.floor((effectivePot * fraction) / 100) * 100;
    const minRaise = getMinRaiseTotal(stack);
    const clamped = Math.max(minRaise, Math.min(stack, raw));
    return clamped;
}

function setRaiseAmount(amount) {
    const slider = document.getElementById('raiseSlider');
    const minRaise = Number(slider.min);
    const maxRaise = Number(slider.max);
    const clamped = Math.max(minRaise, Math.min(maxRaise, Math.round(Number(amount) || 0)));
    slider.value = clamped;
    document.getElementById('raiseAmountInline').value = (clamped / 100).toFixed(2);
}

function updateRaiseQuickButtons() {
    const presets = [
        { id: 'btnOneThirdPot', label: '1/3池', fraction: 1 / 3 },
        { id: 'btnHalfPot', label: '1/2池', fraction: 1 / 2 },
        { id: 'btnFullPot', label: '满池', fraction: 1 },
        { id: 'btnOneHalfPot', label: '1.5x池', fraction: 1.5 }
    ];
    const me = gameState.players.find((p) => p.playerId === String(gameState.playerId));
    const stack = me ? me.stack : 0;
    const minRaise = getMinRaiseTotal(stack);

    presets.forEach((p) => {
        const btn = document.getElementById(p.id);
        if (!btn) return;
        const target = getRaiseAmountByPotFraction(p.fraction);
        const allIn = target >= stack && stack > 0;
        btn.textContent = `${p.label} ¥${toYuan(target)}${allIn ? ' (All-in)' : ''}`;
        btn.disabled = stack <= 0 || (target < minRaise && !allIn);
    });
}

document.addEventListener('input', (e) => {
    if (e.target && e.target.id === 'raiseSlider') {
        document.getElementById('raiseAmountInline').value = (Number(e.target.value) / 100).toFixed(2);
    }
});

async function playerRaise() {
    const raiseYuan = parseFloat(document.getElementById('raiseAmountInline').value);
    if (!raiseYuan || raiseYuan <= 0) {
        alert('请输入有效加注金额');
        return;
    }
    const totalAmount = Math.round(raiseYuan * 100);
    if (totalAmount < gameState.minRaise && totalAmount < (gameState.players.find((p) => p.playerId === String(gameState.playerId))?.stack || 0)) {
        alert('低于最小加注');
        return;
    }
    const extra = Math.max(0, totalAmount - gameState.currentBetThisStreet);
    await sendAction('RAISE', extra);
}

function copyLink() {
    const linkInput = document.getElementById('shareLink');
    linkInput.select();
    document.execCommand('copy');

    const btn = document.querySelector('button[onclick="copyLink()"]');
    const originalText = btn.textContent;
    btn.textContent = '已复制';
    btn.style.background = '#28a745';

    setTimeout(() => {
        btn.textContent = originalText;
        btn.style.background = '';
    }, 1500);
}

function shareRoom() {
    copyLink();
}

function openSettings() {
    alert('设置面板即将接入（音效/振动/主题）');
}

async function startGame() {
    if (gameState.players.length < 2) {
        alert('至少需要 2 人入座才能开始');
        return;
    }

    try {
        const res = await fetch(`/api/game/tables/${gameState.tableId}/start`, { method: 'POST' });
        const data = await res.json();
        if (data.code !== 200) {
            alert(data.message || '开始游戏失败');
            return;
        }
        gameState.gameStatus = 'PRE_FLOP';
        updateStatus('游戏进行中', 'success');
        await refreshTableState();
    } catch (err) {
        console.error(err);
        alert('开始游戏失败，请稍后重试');
    }
}

function leaveTable() {
    const confirmed = confirm('确定要离开房间吗？');
    if (!confirmed) return;
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
    sessionStorage.clear();
    window.location.reload();
}

function openRebuyModal() {
    const modal = new bootstrap.Modal(document.getElementById('rebuyModal'));
    modal.show();
}

async function submitRebuy() {
    const amountYuan = parseFloat(document.getElementById('rebuyAmount').value);
    if (!amountYuan || amountYuan <= 0) {
        alert('请输入有效买入金额');
        return;
    }

    try {
        const res = await fetch(`/api/game/tables/${gameState.tableId}/rebuy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId: gameState.playerId, amount: Math.round(amountYuan * 100) })
        });
        const data = await res.json();
        if (data.code !== 200) {
            alert(data.message || '买入失败');
            return;
        }
        const modal = bootstrap.Modal.getInstance(document.getElementById('rebuyModal'));
        if (modal) modal.hide();
        await refreshTableState();
    } catch (err) {
        console.error(err);
        alert('买入失败，请稍后重试');
    }
}

function updateUI() {
    document.getElementById('gameState').textContent = getGameStateLabel(gameState.gameStatus);
    document.getElementById('potAmount').textContent = toYuan(gameState.potAmount);
    renderPotBreakdown();
    document.getElementById('playerCount').textContent = gameState.players.length;
    document.getElementById('blindInfo').textContent = `¥${toYuan(gameState.smallBlindAmount)} / ¥${toYuan(gameState.bigBlindAmount)}`;
    document.getElementById('currentPlayer').textContent = currentPlayerName();
    document.getElementById('myStack').textContent = `¥${toYuan(gameState.myStack)}`;
    document.getElementById('recentAction').textContent = gameState.recentAction;

    const now = Date.now();
    const seconds = gameState.actionDeadline > now ? Math.ceil((gameState.actionDeadline - now) / 1000) : 0;
    const timerVisible = gameState.currentPlayerToActSeat >= 0 && ['PRE_FLOP', 'FLOP', 'TURN', 'RIVER'].includes(gameState.gameStatus);

    document.getElementById('timer').style.display = timerVisible ? 'block' : 'none';
    document.getElementById('turnTimer').textContent = seconds;
    document.getElementById('timeLeft').textContent = timerVisible ? `${seconds}s` : '-';

    const checkCallBtn = document.getElementById('btnCheckCall');
    checkCallBtn.textContent = gameState.myToCall > 0
        ? `跟注 Call ¥${toYuan(gameState.myToCall)}`
        : '让牌 Check';
    const raiseBtn = document.getElementById('btnRaise');
    raiseBtn.textContent = gameState.currentBetThisStreet > 0 ? '加注 Raise' : '下注 Bet';

    const meActive = gameState.players.find((p) => p.playerId === String(gameState.playerId));
    const myTurn = gameState.mySeat >= 0 && gameState.mySeat === gameState.currentPlayerToActSeat;
    const enableActions = gameState.connected &&
        ['PRE_FLOP', 'FLOP', 'TURN', 'RIVER'].includes(gameState.gameStatus) &&
        myTurn &&
        meActive &&
        meActive.status === 'ACTIVE';

    document.getElementById('btnFold').disabled = !enableActions;
    document.getElementById('btnCheckCall').disabled = !enableActions;
    document.getElementById('btnRaise').disabled = !enableActions;
    document.getElementById('btnAllIn').disabled = !enableActions;

    const nowDebug = Date.now();
    if (nowDebug - lastUiDebugAt > 1800) {
        console.log('[ui:state]', {
            phase: gameState.gameStatus,
            currentSeat: gameState.currentPlayerToActSeat,
            mySeat: gameState.mySeat,
            myStatus: gameState.myStatus,
            toCall: gameState.myToCall,
            actionEnabled: enableActions,
            deadline: gameState.actionDeadline,
            now: nowDebug
        });
        lastUiDebugAt = nowDebug;
    }

    document.getElementById('rebuyWrap').style.display = (gameState.myStack <= 0 && ['WAITING', 'SHOWDOWN'].includes(gameState.gameStatus)) ? 'block' : 'none';
    const floatingStartBtn = document.getElementById('floatingStartBtn');
    if (floatingStartBtn) {
        floatingStartBtn.style.display =
            (gameState.connected && ['WAITING', 'SHOWDOWN'].includes(gameState.gameStatus) && gameState.players.length >= 2)
                ? 'inline-block'
                : 'none';
    }
    if (!enableActions) {
        document.getElementById('raiseTools').style.display = 'none';
        document.getElementById('raiseSubmit').style.display = 'none';
    } else if (document.getElementById('raiseTools').style.display !== 'none') {
        updateRaiseQuickButtons();
    }

    updatePlayerSeats();
    updateCommunityCards();
}

function updatePlayerSeats() {
    const seatsContainer = document.getElementById('playerSeats');
    seatsContainer.innerHTML = '';

    gameState.players.forEach((player, index) => {
        const seatDiv = document.createElement('div');
        seatDiv.className = 'player-card seat-' + (player.seat >= 0 ? player.seat : index);

        if (String(player.playerId) === String(gameState.playerId)) {
            seatDiv.classList.add('active');
        }
        if (player.seat === gameState.currentPlayerToActSeat) {
            seatDiv.classList.add('current-turn');
        }
        if (player.status === 'FOLDED') {
            seatDiv.style.opacity = '0.45';
        }

        const isCurrentTurn = player.seat === gameState.currentPlayerToActSeat;
        const statusColor = player.status === 'FOLDED' ? '#999' :
            player.status === 'ALL_IN' ? '#ff6b6b' : '#90ee90';

        const badge = buildSeatBadge(player);
        const cardsHtml = renderPlayerCards(player);
        const lastActionText = renderLastAction(player.lastAction);

        seatDiv.innerHTML = `
            ${badge}
            <div class="player-name">${player.nickname}</div>
            <div class="player-stack">¥${toYuan(player.stack)}</div>
            <div class="player-status" style="color: ${statusColor};">${getPlayerDisplayStatus(player, isCurrentTurn)}</div>
            <div class="player-hole-cards">${cardsHtml}</div>
            <div class="turn-countdown">${isCurrentTurn ? renderTurnCountdownText() : ''}</div>
            <div class="last-action">${lastActionText}</div>
        `;

        seatsContainer.appendChild(seatDiv);
    });
}

function buildSeatBadge(player) {
    const tags = [];
    if (player.seat === gameState.buttonSeat || player.isDealer) tags.push('D');
    if (player.seat === gameState.smallBlindSeat || player.isSmallBlind) tags.push('SB');
    if (player.seat === gameState.bigBlindSeat || player.isBigBlind) tags.push('BB');
    if (player.status === 'ALL_IN') tags.push('ALL-IN');
    if (!tags.length) return '';
    return `<div class="seat-badge">${tags.join(' · ')}</div>`;
}

function renderPlayerCards(player) {
    const isMe = String(player.playerId) === String(gameState.playerId);
    const revealed = gameState.revealedHoleCards[String(player.playerId)] || null;

    if (isMe && gameState.myHoleCards.length === 2) {
        return gameState.myHoleCards.map((c) => `<div class="mini-card">${cardToDisplay(c)}</div>`).join('');
    }

    if (revealed && revealed.length === 2) {
        return revealed.map((c) => `<div class="mini-card">${cardToDisplay(c)}</div>`).join('');
    }

    if (['PRE_FLOP', 'FLOP', 'TURN', 'RIVER', 'SHOWDOWN'].includes(gameState.gameStatus) && player.status !== 'FOLDED') {
        return '<div class="mini-card back">##</div><div class="mini-card back">##</div>';
    }

    return '<div class="mini-card back" style="opacity:0.2;">##</div><div class="mini-card back" style="opacity:0.2;">##</div>';
}

function renderLastAction(action) {
    if (!action) return '';
    const amount = action.betAmount ? ` ¥${toYuan(action.betAmount)}` : '';
    return `${action.action}${amount}`;
}

function updateCommunityCards() {
    const container = document.getElementById('communityCards');
    container.innerHTML = '';

    for (let i = 0; i < 5; i++) {
        const cardDiv = document.createElement('div');
        if (i < gameState.communityCards.length) {
            cardDiv.className = 'card';
            cardDiv.textContent = cardToDisplay(gameState.communityCards[i]);
        } else {
            cardDiv.className = 'card empty';
            cardDiv.textContent = '?';
        }
        container.appendChild(cardDiv);
    }
}

function renderPotBreakdown() {
    const sidePots = document.getElementById('sidePots');
    if (!sidePots) return;
    if (!gameState.potBreakdown || gameState.potBreakdown.length <= 1) {
        sidePots.textContent = '';
        return;
    }
    sidePots.innerHTML = gameState.potBreakdown
        .map((p) => `${p.name}: ¥${toYuan(p.amount)}`)
        .join('<br>');
}

function updateStatus(text, type = 'success') {
    const badge = document.getElementById('statusBadge');
    badge.textContent = text;
    badge.className = 'status-badge';
    if (type === 'danger') {
        badge.classList.add('disconnected');
    }
}

function getGameStateLabel(state) {
    const labels = {
        WAITING: '等待中',
        DEALING: '发牌中',
        PRE_FLOP: '翻牌前',
        FLOP: '翻牌',
        TURN: '转牌',
        RIVER: '河牌',
        SHOWDOWN: '摊牌',
        CLEANUP: '结算中'
    };
    return labels[state] || state;
}

function getStatusLabel(status) {
    const labels = {
        ACTIVE: '行动中',
        FOLDED: '弃牌',
        ALL_IN: '全下',
        SITTING: '已入座',
        WAITING: '等待中'
    };
    return labels[status] || status;
}

function getPlayerDisplayStatus(player, isCurrentTurn) {
    if (player.status === 'FOLDED') return '弃牌';
    if (player.status === 'ALL_IN') return '全下';
    if (player.status === 'SITTING') return '已入座';
    if (player.status === 'ACTIVE') return isCurrentTurn ? '行动中' : '等待';
    return getStatusLabel(player.status);
}

function renderTurnCountdownText() {
    const now = Date.now();
    const seconds = gameState.actionDeadline > now ? Math.ceil((gameState.actionDeadline - now) / 1000) : 0;
    return `${seconds}s`;
}

function cardToDisplay(card) {
    if (!card) return '?';

    if (typeof card === 'object' && card.rank && card.suit) {
        const rankMap = {
            TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6',
            SEVEN: '7', EIGHT: '8', NINE: '9', TEN: '10', JACK: 'J', QUEEN: 'Q', KING: 'K', ACE: 'A'
        };
        const suitMap = { SPADE: '♠', HEART: '♥', DIAMOND: '♦', CLUB: '♣' };
        return `${rankMap[card.rank] || card.rank}${suitMap[card.suit] || card.suit}`;
    }

    if (typeof card === 'string' && card.length >= 2) {
        const ranks = { A: 'A', K: 'K', Q: 'Q', J: 'J', T: '10' };
        const suits = { H: '♥', D: '♦', C: '♣', S: '♠' };
        return `${ranks[card[0]] || card[0]}${suits[card[1]] || card[1]}`;
    }

    return '?';
}

function extractCommunityCards(currentHand) {
    if (!currentHand || !Array.isArray(currentHand.communityCards)) {
        return [];
    }
    return currentHand.communityCards.filter((card) => !!card);
}

function extractMyHoleCards(currentHand, playerId) {
    if (!currentHand || !currentHand.playerHoleCards || !playerId) {
        return [];
    }
    const cards = currentHand.playerHoleCards[String(playerId)];
    return Array.isArray(cards) ? cards.filter((card) => !!card) : [];
}

function currentPlayerName() {
    if (gameState.currentPlayerToActSeat < 0) return '-';
    const current = gameState.players.find((p) => p.seat === gameState.currentPlayerToActSeat);
    return current ? current.nickname : '-';
}

function toYuan(cents) {
    return (Number(cents || 0) / 100).toFixed(2);
}

document.addEventListener('DOMContentLoaded', initializeApp);
