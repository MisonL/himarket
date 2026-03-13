import React, { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { message, Spin } from 'antd'
import APIs from '../lib/apis'

const CasCallback: React.FC = () => {
  const location = useLocation()
  const navigate = useNavigate()
  const [, setLoading] = useState(true)
  const processedRef = useRef(false)

  useEffect(() => {
    if (!processedRef.current) {
      processedRef.current = true
      handleCasCallbackProcess()
    }
  }, [location.search]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleCasCallbackProcess = async () => {
    try {
      setLoading(true)

      const searchParams = new URLSearchParams(location.search)
      const ticket = searchParams.get('ticket')
      const state = searchParams.get('state')

      if (!ticket || !state) {
        message.error('回调参数不完整，请重试')
        navigate('/login', { replace: true })
        return
      }

      const authResult = await APIs.handleCasCallback({ ticket, state })
      if (!authResult?.data?.access_token) {
        throw new Error('未获取到访问令牌')
      }

      localStorage.setItem('access_token', authResult.data.access_token)
      message.success('登录成功！')
      navigate('/', { replace: true })
    } catch (error) {
      let errorMessage = '登录失败，请重试'

      if (error && typeof error === 'object' && 'response' in error) {
        const axiosError = error as { response?: { status: number } }
        if (axiosError.response?.status === 400) {
          errorMessage = 'CAS票据无效或已过期'
        } else if (axiosError.response?.status === 404) {
          errorMessage = 'CAS配置不存在'
        }
      } else if (error instanceof Error && error.message) {
        errorMessage = error.message
      }

      message.error(errorMessage)
      navigate('/login', { replace: true })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="text-center">
        <Spin size="large" />
        <div className="mt-4 text-gray-600">
          正在处理登录信息...
        </div>
      </div>
    </div>
  )
}

export default CasCallback
