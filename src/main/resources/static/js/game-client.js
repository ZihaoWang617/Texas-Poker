/**
 * WePoker 游戏客户端 JavaScript
 * 
 * 负责：
 * - WebSocket 连接管理
 * - 游戏逻辑交互
 * - UI 实时更新
 * - 玩家操作处理
 */

// 全局游戏状态
const gameState = {
    connected: false,
    sessionId: null,
    playerId: null,
    nickname: null,
    tableId: null,
    myStack: 0,
    mySeat: -1,
    gameStatus: 'WAITING',
    potAmount: 0,
    players: [],
    communityCards: [],
    currentPlayerToAct: -1,
    timeRemaining: 0
};

// WebSocket 连接
let ws = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;

/**
 * 初始化应用
 */
function initializeApp() {
    // 获取 URL 参数
    const urlParams = new URLSearchParams(window.location.search);
    let tableIdFromUrl = urlParams.get('table');
    
    // 如果没有 table ID，生成一个
    if (!tableIdFromUrl) {
        tableIdFromUrl = Math.floor(Math.random() * 1000000).toString();
    }
    
    gameState.tableId = parseInt(tableIdFromUrl);
    
    // 生成或使用提供的 playerId
    if (!sessionStorage.getItem('playerId')) {
        sessionStorage.setItem('playerId', 'player_' + Math.random().toString(36).substr(2, 9));
    }
    gameState.playerId = parseInt(sessionStorage.getItem('playerId').replace('player_', '')) || Math.floor(Math.random() * 1000000);
    
    // 设置分享链接 - 在进入页面时就生成
    const baseUrl = window.location.origin + window.location.pathname;
    const roomUrl = `${baseUrl}?table=${gameState.tableId}`;
    document.getElementById('shareLink').value = roomUrl;
    document.getElementById('tableId').textContent = gameState.tableId;
    
    // 显示加入对话框
    const joinModal = new bootstrap.Modal(document.getElementById('joinModal'));
    joinModal.show();
    
    // 定时更新 UI
    setInterval(updateUI, 500);
}

/**
 * 加入房间
 */
function joinTable() {
    const nickname = document.getElementById('inputNickname').value.trim();
    const buyIn = parseInt(document.getElementById('inputBuyIn').value);
    
    // 验证输入
    if (!nickname || nickname.length < 2 || nickname.length > 20) {
        alert('昵称长度必须在 2-20 个字符之间');
        return;
    }
    
    if (!buyIn || buyIn < 50 || buyIn > 5000) {
        alert('买入金额必须在 ¥50-¥5000 之间');
        return;
    }
    
    // 如果还没有表 ID，从输入创建
    if (!gameState.tableId) {
        gameState.tableId = Math.floor(Math.random() * 1000000);
        document.getElementById('tableId').textContent = gameState.tableId;
    }
    
    gameState.nickname = nickname;
    gameState.myStack = buyIn * 100; // 转换为分
    
    // 关闭对话框
    bootstrap.Modal.getInstance(document.getElementById('joinModal')).hide();
    
    // 连接到服务器
    connectToServer();
}

/**
 * 连接到服务器（或模拟连接）
 */
function connectToServer() {
    try {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws`;
        
        // 尝试连接到实际 WebSocket 服务器
        ws = new WebSocket(wsUrl);
        let connectionTimeout = null;
        
        // 5 秒超时 - 如果连接失败，使用离线模式
        connectionTimeout = setTimeout(() => {
            if (ws && ws.readyState === WebSocket.CONNECTING) {
                console.log('WebSocket 连接超时，使用离线演示模式');
                ws.close();
                enableOfflineMode();
            }
        }, 5000);
        
        ws.onopen = () => {
            clearTimeout(connectionTimeout);
            console.log('WebSocket 已连接');
            gameState.connected = true;
            updateStatus('已连接', 'success');
            sendHandshake();
        };
        
        ws.onmessage = (event) => {
            handleMessage(event.data);
        };
        
        ws.onerror = (error) => {
            clearTimeout(connectionTimeout);
            console.error('WebSocket 错误:', error);
            console.log('启用离线演示模式');
            enableOfflineMode();
        };
        
        ws.onclose = () => {
            clearTimeout(connectionTimeout);
            console.log('WebSocket 已断开');
            if (gameState.connected) {
                gameState.connected = false;
                updateStatus('已断开连接', 'danger');
                
                // 尝试重新连接
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++;
                    console.log(`尝试重新连接 (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})...`);
                    setTimeout(connectToServer, 3000);
                } else {
                    console.log('达到最大重新连接次数，启用演示模式');
                    enableOfflineMode();
                }
            }
        };
        
    } catch (error) {
        console.error('连接失败:', error);
        console.log('启用离线演示模式');
        enableOfflineMode();
    }
}

/**
 * 启用离线演示模式（当没有后端服务器时）
 */
function enableOfflineMode() {
    console.log('启用离线演示模式...');
    gameState.connected = true;
    gameState.sessionId = 'demo_' + Math.random().toString(36).substr(2, 9);
    
    updateStatus('演示模式 (本地)', 'warning');
    
    // 模拟添加其他玩家
    addDemoPlayers();
    
    // 自动更新游戏UI
    simulateGamePlay();
}

/**
 * 发送握手消息
 */
function sendHandshake() {
    const handshake = {
        messageId: generateMessageId(),
        type: 'HANDSHAKE',
        playerId: gameState.playerId,
        timestamp: Date.now()
    };
    
    sendMessage(handshake);
}

/**
 * 处理来自服务器的消息
 */
function handleMessage(data) {
    try {
        // 移除尾部换行符
        const message = JSON.parse(data.trim());
        
        console.log('收到消息:', message.type, message);
        
        switch (message.type) {
            case 'ACK':
                handleAck(message);
                break;
            case 'GAME_STATE_UPDATE':
                handleGameStateUpdate(message);
                break;
            case 'POT_UPDATE':
                handlePotUpdate(message);
                break;
            case 'BOARD_UPDATE':
                handleBoardUpdate(message);
                break;
            case 'ERROR':
                handleError(message);
                break;
            case 'RESULT':
                handleResult(message);
                break;
            case 'TIME_WARNING':
                handleTimeWarning(message);
                break;
        }
    } catch (error) {
        console.error('处理消息失败:', error, data);
    }
}

/**
 * 处理握手 ACK
 */
function handleAck(message) {
    gameState.sessionId = message.sessionId;
    gameState.connected = true;
    reconnectAttempts = 0;
    
    updateStatus('已连接', 'success');
    
    // 发送加入房间消息
    const joinMsg = {
        messageId: generateMessageId(),
        type: 'JOIN_TABLE',
        tableId: gameState.tableId,
        playerId: gameState.playerId,
        sessionId: gameState.sessionId,
        timestamp: Date.now(),
        sequenceNumber: 1,
        payload: {
            buyIn: gameState.myStack,
            nickname: gameState.nickname
        }
    };
    
    sendMessage(joinMsg);
}

/**
 * 处理游戏状态更新
 */
function handleGameStateUpdate(message) {
    const payload = message.payload || {};
    
    gameState.gameStatus = payload.gameState || gameState.gameStatus;
    gameState.potAmount = payload.pot || 0;
    gameState.players = payload.players || [];
    gameState.currentPlayerToAct = payload.currentPlayerToAct || -1;
    gameState.timeRemaining = payload.timeRemaining || 0;
    gameState.myStack = payload.myStack || gameState.myStack;
    
    // 更新社区牌
    if (payload.board) {
        gameState.communityCards = payload.board;
    }
    
    // 更新我的座位
    if (payload.mySeat !== undefined) {
        gameState.mySeat = payload.mySeat;
    }
    
    console.log('游戏状态:', gameState.gameStatus, '玩家数:', gameState.players.length);
}

/**
 * 处理底池更新
 */
function handlePotUpdate(message) {
    gameState.potAmount = message.payload?.amount || 0;
}

/**
 * 处理板面更新
 */
function handleBoardUpdate(message) {
    gameState.communityCards = message.payload?.cards || [];
}

/**
 * 处理错误
 */
function handleError(message) {
    console.error('服务器错误:', message.errorMessage);
    updateStatus(`错误: ${message.errorMessage}`, 'danger');
    if (message.errorCode === 'PROCESS_ERROR') {
        setTimeout(() => {
            alert('发生错误，请刷新页面重试');
        }, 1000);
    }
}

/**
 * 处理游戏结果
 */
function handleResult(message) {
    const payload = message.payload || {};
    const winner = payload.winner || '未知';
    const amount = payload.amount || 0;
    
    alert(`游戏结束！赢家: ${winner}，奖励: ¥${(amount / 100).toFixed(2)}`);
}

/**
 * 处理时间警告
 */
function handleTimeWarning(message) {
    gameState.timeRemaining = message.payload?.timeLeft || 0;
}

/**
 * 发送消息到服务器
 */
function sendMessage(message) {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
        console.error('WebSocket 未连接');
        return;
    }
    
    // 添加序列号
    if (!message.sequenceNumber) {
        message.sequenceNumber = Math.floor(Math.random() * 1000);
    }
    
    if (!message.messageId) {
        message.messageId = generateMessageId();
    }
    
    if (!message.timestamp) {
        message.timestamp = Date.now();
    }
    
    if (!message.playerId) {
        message.playerId = gameState.playerId;
    }
    
    if (!message.tableId && gameState.tableId) {
        message.tableId = gameState.tableId;
    }
    
    if (!message.sessionId && gameState.sessionId) {
        message.sessionId = gameState.sessionId;
    }
    
    const messageStr = JSON.stringify(message) + '\n';
    console.log('发送消息:', message.type);
    ws.send(messageStr);
}

/**
 * 玩家操作：过牌
 */
function playerCheck() {
    sendMessage({
        type: 'CHECK'
    });
}

/**
 * 玩家操作：跟注
 */
function playerCall() {
    sendMessage({
        type: 'CALL'
    });
}

/**
 * 玩家操作：弃牌
 */
function playerFold() {
    sendMessage({
        type: 'FOLD'
    });
}

/**
 * 玩家操作：下注
 */
function playerBet() {
    const amount = parseInt(document.getElementById('betAmount').value);
    
    if (!amount || amount <= 0) {
        alert('请输入有效的下注金额');
        return;
    }
    
    if (amount * 100 > gameState.myStack) {
        alert('筹码不足');
        return;
    }
    
    sendMessage({
        type: 'BET',
        payload: {
            amount: amount * 100 // 转换为分
        }
    });
    
    document.getElementById('betAmount').value = '';
}

/**
 * 玩家操作：加注
 */
function playerRaise() {
    const amount = parseInt(document.getElementById('raiseAmount').value);
    
    if (!amount || amount <= 0) {
        alert('请输入有效的加注金额');
        return;
    }
    
    if (amount * 100 > gameState.myStack) {
        alert('筹码不足');
        return;
    }
    
    sendMessage({
        type: 'RAISE',
        payload: {
            amount: amount * 100 // 转换为分
        }
    });
    
    bootstrap.Modal.getInstance(document.getElementById('raiseModal')).hide();
    document.getElementById('raiseAmount').value = '';
}

/**
 * 玩家操作：全下
 */
function allIn() {
    if (gameState.myStack <= 0) {
        alert('没有筹码可下');
        return;
    }
    
    const confirmed = confirm(`确定全下 ¥${(gameState.myStack / 100).toFixed(2)} 吗？`);
    if (!confirmed) return;
    
    sendMessage({
        type: 'ALL_IN'
    });
}

/**
 * 离开房间
 */
function leaveTable() {
    const confirmed = confirm('确定要离开房间吗？');
    if (!confirmed) return;
    
    sendMessage({
        type: 'LEAVE_TABLE'
    });
    
    // 断开连接
    if (ws) {
        ws.close();
    }
    
    // 清空本地数据
    sessionStorage.clear();
    
    // 重新整页
    setTimeout(() => {
        window.location.reload();
    }, 500);
}

/**
 * 显示加注对话框
 */
function showBetDialog() {
    if (gameState.myStack <= 0) {
        alert('没有筹码可加注');
        return;
    }
    
    const raiseModal = new bootstrap.Modal(document.getElementById('raiseModal'));
    raiseModal.show();
}

/**
 * 复制房间链接
 */
function copyLink() {
    const linkInput = document.getElementById('shareLink');
    linkInput.select();
    document.execCommand('copy');
    
    // 显示提示
    const btn = event.target;
    const originalText = btn.textContent;
    btn.textContent = '已复制';
    btn.style.background = '#28a745';
    
    setTimeout(() => {
        btn.textContent = originalText;
        btn.style.background = '';
    }, 2000);
}

/**
 * 开始游戏
 */
function startGame() {
    if (gameState.players.length < 1) {
        alert('至少需要 1 个其他玩家');
        return;
    }
    
    if (gameState.gameStatus !== 'WAITING') {
        alert('游戏已开始，无法再开始');
        return;
    }
    
    gameState.gameStatus = 'PRE_FLOP';
    updateStatus('游戏进行中', 'success');
    
    // 发送消息给服务器
    sendMessage({
        messageId: generateMessageId(),
        type: 'START_GAME',
        tableId: gameState.tableId,
        playerId: gameState.playerId,
        timestamp: Date.now()
    });
}

/**
 * 更新 UI
 */
function updateUI() {
    // 更新游戏状态
    document.getElementById('gameState').textContent = getGameStateLabel(gameState.gameStatus);
    
    // 更新底池
    document.getElementById('potAmount').textContent = (gameState.potAmount / 100).toFixed(2);
    
    // 更新我的信息
    document.getElementById('myNickname').textContent = gameState.nickname || '-';
    document.getElementById('myStack').textContent = '¥' + (gameState.myStack / 100).toFixed(2);
    document.getElementById('mySeat').textContent = gameState.mySeat >= 0 ? gameState.mySeat + 1 : '-';
    document.getElementById('playerCount').textContent = gameState.players.length;
    
    // 更新当前玩家
    if (gameState.currentPlayerToAct >= 0) {
        const currentPlayer = gameState.players.find(p => p.playerId === gameState.currentPlayerToAct);
        document.getElementById('currentPlayer').textContent = currentPlayer?.nickname || '-';
    }
    
    // 更新时间
    if (gameState.timeRemaining > 0) {
        document.getElementById('timer').style.display = 'block';
        document.getElementById('timeLeft').textContent = gameState.timeRemaining;
    } else {
        document.getElementById('timer').style.display = 'none';
    }
    
    // 更新玩家座位显示
    updatePlayerSeats();
    
    // 更新社区牌
    updateCommunityCards();
    
    // 更新操作按钮状态
    updateActionButtons();
}

/**
 * 更新玩家座位显示
 */
function updatePlayerSeats() {
    const seatsContainer = document.getElementById('playerSeats');
    
    // 清除旧的座位
    seatsContainer.innerHTML = '';
    
    // 添加玩家座位
    gameState.players.forEach((player, index) => {
        const seatDiv = document.createElement('div');
        seatDiv.className = 'player-card seat-' + index;
        
        if (player.playerId === gameState.playerId) {
            seatDiv.classList.add('active');
        }
        
        const statusColor = player.status === 'FOLDED' ? '#999' : 
                           player.status === 'ALL_IN' ? '#ff6b6b' : '#90ee90';
        
        seatDiv.innerHTML = `
            <div class="player-name">${player.nickname || '玩家' + index}</div>
            <div class="player-stack">¥${(player.stack / 100).toFixed(2)}</div>
            <div class="player-status" style="color: ${statusColor};">${getStatusLabel(player.status)}</div>
        `;
        
        seatsContainer.appendChild(seatDiv);
    });
}

/**
 * 更新社区牌显示
 */
function updateCommunityCards() {
    const container = document.getElementById('communityCards');
    container.innerHTML = '';
    
    // 显示 5 张社区牌位置
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

/**
 * 更新操作按钮状态
 */
function updateActionButtons() {
    const isMyTurn = gameState.currentPlayerToAct === gameState.playerId;
    const hasStack = gameState.myStack > 0;
    
    document.getElementById('btnCheck').disabled = !isMyTurn || !hasStack;
    document.getElementById('btnCall').disabled = !isMyTurn || !hasStack;
    document.getElementById('btnFold').disabled = !isMyTurn || !hasStack;
    document.getElementById('btnRaise').disabled = !isMyTurn || !hasStack;
}

/**
 * 更新连接状态显示
 */
function updateStatus(text, type = 'success') {
    const badge = document.getElementById('statusBadge');
    badge.textContent = text;
    badge.className = 'status-badge';
    if (type === 'danger') {
        badge.classList.add('disconnected');
    }
}

/**
 * 获取游戏状态标签
 */
function getGameStateLabel(state) {
    const labels = {
        'WAITING': '等待中',
        'DEALING': '发牌中',
        'PRE_FLOP': '翻牌前',
        'FLOP': '翻牌',
        'TURN': '转牌',
        'RIVER': '河牌',
        'SHOWDOWN': '秀牌',
        'CLEANUP': '结算中'
    };
    return labels[state] || state;
}

/**
 * 获取玩家状态标签
 */
function getStatusLabel(status) {
    const labels = {
        'ACTIVE': '活跃',
        'FOLDED': '弃牌',
        'ALL_IN': '全下',
        'SITTING': '已入座',
        'WAITING': '等待中'
    };
    return labels[status] || status;
}

/**
 * 将牌转换为显示格式
 */
function cardToDisplay(card) {
    // card 格式: "AH" (Ace of Hearts)
    const ranks = { 'A': 'A', 'K': 'K', 'Q': 'Q', 'J': 'J', 'T': '10' };
    const suits = { 'H': '♥', 'D': '♦', 'C': '♣', 'S': '♠' };
    
    if (!card || card.length < 2) return '?';
    
    const rank = ranks[card[0]] || card[0];
    const suit = suits[card[1]] || card[1];
    
    return rank + suit;
}

/**
 * 生成唯一的消息 ID
 */
function generateMessageId() {
    return 'msg_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
}

/**
 * 演示模式：添加 AI 玩家
 */
function addDemoPlayers() {
    const demoNames = ['Alice', 'Bob', 'Charlie', 'Diana', 'Eva'];
    const demoCount = Math.floor(Math.random() * 3) + 2; // 2-4 个其他玩家
    
    for (let i = 0; i < demoCount; i++) {
        gameState.players.push({
            playerId: 1000 + i,
            nickname: demoNames[i % demoNames.length],
            stack: Math.floor(Math.random() * 3000) + 1000,
            seat: i,
            status: 'ACTIVE',
            holeCards: ['AS', 'KH']
        });
    }
}

/**
 * 演示模式：模拟游戏进行
 */
function simulateGamePlay() {
    // 模拟游戏状态变化
    const states = ['WAITING', 'PRE_FLOP', 'FLOP', 'TURN', 'RIVER', 'SHOWDOWN'];
    let stateIndex = 0;
    
    setInterval(() => {
        gameState.gameStatus = states[stateIndex % states.length];
        gameState.potAmount += Math.floor(Math.random() * 200) + 50;
        gameState.currentPlayerToAct = gameState.players[Math.floor(Math.random() * gameState.players.length)]?.playerId || gameState.playerId;
        
        // 更改社区牌
        if (gameState.gameStatus === 'FLOP') {
            gameState.communityCards = ['AH', 'KD', '5C'];
        } else if (gameState.gameStatus === 'TURN') {
            gameState.communityCards = ['AH', 'KD', '5C', '3S'];
        } else if (gameState.gameStatus === 'RIVER') {
            gameState.communityCards = ['AH', 'KD', '5C', '3S', '2H'];
        }
        
        stateIndex++;
        updateUI();
    }, 8000);
}

// 页面加载时初始化
document.addEventListener('DOMContentLoaded', initializeApp);

// 页面关闭时清理连接
window.addEventListener('beforeunload', () => {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.close();
    }
});

