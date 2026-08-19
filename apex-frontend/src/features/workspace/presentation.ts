import type {
  HumanPromptRecord,
  SessionViewModel,
  ToolConfirmationEditableField,
  ToolConfirmationRecord,
} from '@/types/apex'

const sessionStatusLabels: Record<SessionViewModel['status'], string> = {
  idle: '待开始',
  streaming: '处理中',
  'waiting-intervention': '等待人工介入',
  completed: '已完成',
  aborted: '已停止',
  error: '异常',
}

const promptTypeLabels: Record<HumanPromptRecord['inputType'], string> = {
  TEXT_INPUT: '文本输入',
  SINGLE_SELECT: '单选',
  CONFIRM: '确认',
  MULTI_SELECT: '多选',
}

const confirmationRiskLabels: Record<string, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
}

const editableFieldTypeLabels: Record<ToolConfirmationEditableField['input_type'], string> = {
  text: '文本',
  textarea: '长文本',
  'single-select': '单选',
  confirm: '确认',
  date: '日期',
  datetime: '日期时间',
}

export function formatSessionStatus(status: SessionViewModel['status']): string {
  return sessionStatusLabels[status] ?? status
}

export function formatPromptInputType(type: HumanPromptRecord['inputType']): string {
  return promptTypeLabels[type] ?? type
}

export function formatRuntimeStatus(status: string): string {
  const normalized = status.toUpperCase()

  if (normalized.includes('CANCEL')) {
    return '已取消'
  }

  if (normalized.includes('COMPLETE') || normalized.includes('SUCCESS')) {
    return '已完成'
  }

  if (normalized.includes('RUNNING') || normalized.includes('ACTIVE') || normalized.includes('STREAMING')) {
    return '进行中'
  }

  if (normalized.includes('PENDING') || normalized.includes('WAIT')) {
    return '待处理'
  }

  if (normalized.includes('FAIL') || normalized.includes('ERROR') || normalized.includes('ABORT')) {
    return '失败'
  }

  return status
}

export function toneFromStatus(status: string): 'idle' | 'success' | 'active' | 'warning' | 'danger' {
  const normalized = status.toUpperCase()

  if (normalized.includes('CANCEL')) {
    return 'warning'
  }

  if (normalized.includes('COMPLETE') || normalized.includes('SUCCESS')) {
    return 'success'
  }

  if (normalized.includes('FAIL') || normalized.includes('ERROR') || normalized.includes('ABORT')) {
    return 'danger'
  }

  if (normalized.includes('PENDING') || normalized.includes('WAIT')) {
    return 'warning'
  }

  if (normalized.includes('RUNNING') || normalized.includes('ACTIVE') || normalized.includes('STREAMING')) {
    return 'active'
  }

  return 'idle'
}

export function formatConfirmationRiskLevel(riskLevel: ToolConfirmationRecord['riskLevel']): string {
  return confirmationRiskLabels[riskLevel.toUpperCase()] ?? riskLevel
}

export function formatEditableFieldType(type: ToolConfirmationEditableField['input_type']): string {
  return editableFieldTypeLabels[type] ?? type
}
