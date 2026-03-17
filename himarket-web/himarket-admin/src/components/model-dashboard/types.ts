export interface FilterOptionsState {
  clusterIds: string[]
  apis: string[]
  models: string[]
  routes: string[]
  services: string[]
  consumers: string[]
}

export interface KpiDataState {
  pv: string
  uv: string
  fallbackCount: string
  inputToken: string
  outputToken: string
  totalToken: string
}

export type TableValue = string | number | boolean | null | undefined

export interface TableDataState {
  modelToken: Record<string, TableValue>[]
  consumerToken: Record<string, TableValue>[]
  serviceToken: Record<string, TableValue>[]
  errorRequests: Record<string, TableValue>[]
  ratelimitedConsumer: Record<string, TableValue>[]
  riskLabel: Record<string, TableValue>[]
  riskConsumer: Record<string, TableValue>[]
}
