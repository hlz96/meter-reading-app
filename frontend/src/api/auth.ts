import { client } from './client'
import type {
  AuthResult,
  LoginPayload,
  MeResult,
  RegisterPayload,
  SmsCodePayload,
  SmsCodeResult,
} from '@/types/dto'

// 拦截器已把信封 data 解包到 resp.data,故 client.post<T> 的 r.data 即为 T。

export const authApi = {
  smsCode: (payload: SmsCodePayload) =>
    client.post<SmsCodeResult>('/auth/sms-code', payload).then((r) => r.data),

  register: (payload: RegisterPayload) =>
    client.post<AuthResult>('/auth/register', payload).then((r) => r.data),

  login: (payload: LoginPayload) =>
    client.post<AuthResult>('/auth/login', payload).then((r) => r.data),

  me: () => client.get<MeResult>('/auth/me').then((r) => r.data),
}
