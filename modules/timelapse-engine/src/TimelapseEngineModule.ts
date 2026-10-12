import { NativeModule, requireNativeModule } from 'expo';

export type Plan = { host: string; port: number; intervalMs: number; totalShots: number };
export type Status = { running: boolean; taken: number; total: number };

declare class TimelapseEngineModule extends NativeModule {
  start(plan: Plan): Promise<void>;
  stop(): Promise<void>;
  getStatus(): Status;
}

export default requireNativeModule<TimelapseEngineModule>('TimelapseEngine');