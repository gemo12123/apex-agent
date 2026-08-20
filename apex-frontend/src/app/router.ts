import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'workspace',
      component: () => import('@/features/workspace/pages/WorkspacePage.vue'),
    },
    {
      path: '/conversation',
      name: 'conversation',
      component: () => import('@/features/conversation/pages/ConversationPage.vue'),
    },
  ],
})
