// ============================================================
// service-worker.js  — Web Push 알림 수신 처리
// ============================================================

self.addEventListener('push', event => {
    let data = { title: '사물함 알림', body: '' };
    try {
        data = event.data ? event.data.json() : data;
    } catch (_) {}

    event.waitUntil(
        self.registration.showNotification(data.title ?? '사물함 알림', {
            body: data.body ?? '',
            icon: '/favicon.ico',
            badge: '/favicon.ico'
        })
    );
});

self.addEventListener('notificationclick', event => {
    event.notification.close();
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(cs => {
            for (const c of cs) {
                if (c.url.includes('/student.html') && 'focus' in c) return c.focus();
            }
            if (clients.openWindow) return clients.openWindow('/student.html');
        })
    );
});
