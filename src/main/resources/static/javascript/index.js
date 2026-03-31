import CustomIcon from "./custom/CustomIcon.js";
import CustomHint from "./custom/CustomHint.js";
import CustomAnchor from "./custom/CustomAnchor.js";
import CustomButton from "./custom/CustomButton.js";

const BASE = 'http://localhost:8080';

// 1. Axios와 Fetch의 응답 구조를 { data: ... }로 통일
const ax = axios?.create({
    baseURL: BASE,
    withCredentials: true,
    headers: { 'Content-Type': 'application/json' }
}) || {
    get: (url) => fetch(BASE + url, { credentials: 'include' }).then(async r => ({ data: await r.json() })),
    post: (url, data) => fetch(BASE + url, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }).then(async r => ({ data: await r.json() })),
    patch: (url, data) => fetch(BASE + url, { method: 'PATCH', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }).then(async r => ({ data: await r.json() })),
    delete: (url) => fetch(BASE + url, { method: 'DELETE', credentials: 'include' }).then(async r => ({ data: await r.json() })),
    put: (url, data) => fetch(BASE + url, { method: 'PUT', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }).then(async r => ({ data: await r.json() })),
};

// 2. 공통 요청 래퍼 (try-catch 중복 제거)
async function request(promise) {
    try {
        const r = await promise;
        return r.data; // 이제 axios든 fetch든 항상 .data가 존재함
    } catch (e) {
        const status = e?.response?.status;
        const msg = e?.response?.data?.message || e?.message || '오류가 발생했습니다.';
        return { status, message: msg, error: true };
    }
}

/* ---- API Objects ---- */
const UserAPI = {
    signup: (data) => request(ax.post('/api/users/signup', data)),
    sendMail: (email) => request(ax.post(`/api/users/send-mail?email=${encodeURIComponent(email)}`)),
    verifyMail: (email, code) => request(ax.post(`/api/users/verify-mail?email=${encodeURIComponent(email)}&code=${encodeURIComponent(code)}`)),
    login: (email, password) => request(ax.post('/api/users/login', { email, password })),
    logout: () => request(ax.post('/api/users/logout')),
    getMe: () => request(ax.get('/api/users/me')),
    updateMe: (data) => request(ax.patch('/api/users/me', data)),
    withdraw: () => request(ax.delete('/api/users/withdraw')),
    forgotPassword: (email) => request(ax.post('/api/users/password/forgot', { email })),
    resetPassword: (data) => request(ax.post('/api/users/password/reset', data)),
};

const ChatAPI = {
    getSessions: () => request(ax.get('/api/chat/sessions')),
    getMessages: (sessionId) => request(ax.get(`/api/chat/sessions/${sessionId}/messages`)),
    deleteSession: (sessionId) => request(ax.delete(`/api/chat/sessions/${sessionId}`)),
    ask: (sessionId, message, model) => request(ax.post(`/api/chat/ask${sessionId ? '?sessionId=' + sessionId : ''}`, { message, model })),
    summary: (sessionId, message, model) => request(ax.post(`/api/chat/summary${sessionId ? '?sessionId=' + sessionId : ''}`, { message, model })),
    youtube: (sessionId, message, model) => request(ax.post(`/api/chat/youtube${sessionId ? '?sessionId=' + sessionId : ''}`, { message, model })),
};

const PaymentAPI = {
    getConfig: () => request(ax.get('/api/payments/config')),
    verify: (data) => request(ax.post('/api/payments/verify', data)),
};

const AdminAPI = {
    getUsers: () => request(ax.get('/api/admin/users')),
    changeUserStatus: (userId, status) => request(ax.patch(`/api/admin/users/${userId}/status?status=${status}`)),
    updatePlan: (planId, data) => request(ax.put(`/api/admin/plans/${planId}`, data)),
    resetTokens: () => request(ax.post('/api/admin/tokens/reset')),
    getPlanStats: () => request(ax.get('/api/admin/stats/plans')),
    getModelStats: () => request(ax.get('/api/admin/stats/usage')),
};

/* sidebar */
window.sidebarStatus = window.sidebarStatus || [];

function toggleSidebar() {
    const html = document.documentElement;
    const targetValue = 'collapsed';

    const index = window.sidebarStatus.indexOf(targetValue);

    if (index === -1) {
        window.sidebarStatus.push(targetValue);
    } else {
        window.sidebarStatus.splice(index, 1);
    }

    html.dataset['sidebar'] = window.sidebarStatus.join(' ');

    localStorage.setItem('sidebar-status', JSON.stringify(window.sidebarStatus));
}

window.toggleSidebar = toggleSidebar;

/* textarea */
const textarea = document.getElementById('textarea');

textarea.addEventListener("input", function () {
    textarea.style.height = "48px";
    textarea.style.height = textarea.scrollHeight + "px";
});

/* popup */
const popups = document.querySelectorAll('#popups > .popup');

popups.forEach(popup => {
    let closeBtn = popup.querySelector('custom-button.close');

    if (!closeBtn) {
        closeBtn = document.createElement('custom-button');
        closeBtn.classList.add('close');
        closeBtn.setAttribute('value', '닫기');
        closeBtn.setAttribute('onclick', 'closePopup()');

        const closeIcon = document.createElement('custom-icon');
        closeIcon.setAttribute('icon', 'close');
        closeIcon.setAttribute('size', '24px');

        closeBtn.appendChild(closeIcon);

        popup.prepend(closeBtn);
    }
});

function openPopup(popupName, page = 0) {
    const popup = document.querySelector(`.popup[data-popup="${popupName.trim()}"]`);

    if (!popup) {
        console.error('Could not find popup: ' + popupName);
        return;
    }

    if (!popup.querySelector('.opened:only-child')) {
        closePopup();
    }

    const sections = popup.querySelectorAll('section');

    if (sections.length > 0) {
        sections[page].classList.add('opened');
    } else {
        const nav = popup.querySelector('nav');
        nav.classList.add('opened');
    }
}

window.openPopup = openPopup;

function closePopup() {
    const popups = document.querySelectorAll('.popup[data-popup]:not([data-popup=""])');
    popups.forEach(popup => {
        const opened = popup.querySelectorAll('.opened');

        opened.forEach(item => {
            item.classList.remove('opened');
        });
    });
}

window.closePopup = closePopup;

function togglePassword(element) {
    const input = element.closest('.input');
    const inputElement = input.querySelector('input');
    console.log(input, inputElement);
    const type = inputElement.getAttribute('type');

    if (type ==='password') {
        inputElement.setAttribute('type', 'text');
        element.innerHTML = '<custom-icon icon="show""></custom-icon>';
        element.setAttribute('value', '비밀번호 숨기기');
    } else {
        inputElement.setAttribute('type', 'password');
        element.innerHTML = '<custom-icon icon="hide"></custom-icon>';
        element.setAttribute('value', '비밀번호 보기');
    }
}

window.togglePassword = togglePassword;

async function send(object) {
    const form = object.closest('form');
    const prompt = form.content.textContent.trim();

    const response = await ChatAPI.ask(null, prompt, null);

    if (response.error) {
        alert(response.message);
    }
}

window.send = send;

async function emailVerify(object) {
    const form = object.closest('form');
    const email = form.email.value.trim();
    const code = form.code.value.trim();

    if (code) {
        const response = await UserAPI.verifyMail(email, code);
        if (!response.error) {
            alert('인증 성공');
        } else {
            alert(response.message);
        }
    } else {
        const response = await UserAPI.sendMail(email);
        if (!response.error) {
            alert('인증코드를 보냈습니다');
        } else {
            alert(response.message);
        }

        object.textContent = '인증';
    }
}

window.emailVerify = emailVerify;

async function signup(object) {
    const form = object.closest('form');
    const section = form.closest('section');
    const emailCheck = section.querySelector('.email-check');

    const email = emailCheck.email.value.trim();
    const password = form.password.value.trim();
    const username = form.username.value.trim();

    const response = await UserAPI.signup({ email, password, username });
    if (!response.error) {
        alert('회원가입 성공!');
        openPopup('login');
    } else {
        alert(response.message);
    }
}

window.signup = signup;

async function doLogin(object) {
    const form = object.closest('form');
    const email = form.email.value.trim();
    const password = form.password.value.trim();

    const response = await UserAPI.login(email, password);
    if (!response.error) {
        alert('로그인 성공!')
        location.reload();
    } else {
        alert(response.message);
    }
}

window.doLogin = doLogin;

async function logout (object) {
    const response = await UserAPI.logout(object);
    if (!response.error) {
        alert('로그아웃 성공!')
        location.reload();
    } else {
        alert(response.message);
    }
}

window.logout = logout;

async function getMe() {
    const response = await UserAPI.getMe();

    if (!response.error) {
        document.getElementById('account').innerHTML = `
        <custom-button onclick="openPopup('account')" value="내 정보">
            <custom-icon icon="account"></custom-icon>
            <span class="content">${response.data.email}</span>
        </custom-button>
        `;

        document.querySelectorAll('.leftToken').forEach(item => {
            const amount = item.querySelector('.amount');
            amount.textContent = response.data.remainingTokens;
            item.style.display = 'flex';
        });

        const renderInfo = document.getElementById('renderInfo');
        renderInfo.username.value = response.data.username;
    } else {
        document.getElementById('account').innerHTML = `
        <custom-button onclick="openPopup('login')" value="시작하기">
            <custom-icon icon="account"></custom-icon>
            <span class="content">시작하기</span>
        </custom-button>
        `;

        document.querySelectorAll('.leftToken').forEach(item => {
            item.style.display = 'none';
        });
    }
}

window.getMe = getMe;

getMe();

async function resetPassword(object) {
    const form = object.closest('form');
    const section = form.closest('section');
    const emailCheck = section.querySelector('.email-check');

    const email = emailCheck.email.value.trim();
    const password = form.password.value.trim();
    const username = form.username.value.trim();

    const response = await UserAPI.resetPassword(email, password);
    if (!response.error) {
        alert('비밀번호 재설정 성공!');
        openPopup('login');
    } else {
        alert(response.message);
    }
}

window.resetPassword = resetPassword;

async function updateUser(object) {
    const form = object.closest('form');
    const currentPassword = form.currentPassword.value.trim();
    const newPassword = form.newPassword.value.trim();
    const username = form.username.value.trim()

    const response = await UserAPI.updateMe({ currentPassword, newPassword, username });
    if (!response.error) {
        alert('성공적으로 수정되었습니다!');
    } else {
        alert(response.message);
    }
}

window.updateUser = updateUser;

async function withdraw(){
    const response = await UserAPI.withdraw();
    if (!response.error) {
        alert('성공적으로 탈퇴되었습니다!');
        location.reload();
    } else {
        alert(response.message);
    }
}

window.withdraw = withdraw;

async function getSessions() {
    const response = await ChatAPI.getSessions();
    if (!response.error) {
        const sidebarUl = document.querySelector('#sidebar > section > ul');
        sidebarUl.innerHTML = '';

        response.data.
    }
}