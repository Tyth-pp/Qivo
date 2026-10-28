/** 对旧服务的校验文本兜底，不把正则、异常类或 SQL 展示给用户。 */
export function readableMessage(value: unknown, fallback = '操作未完成，请稍后重试'): string {
  const text = typeof value === 'string' ? value.trim() : '';
  if (!text || /^(请求失败|系统异常|Request failed|Network Error)/i.test(text)) return fallback;
  if (/正则|regexp|must match/i.test(text)) return /username|用户名/i.test(text)
    ? '用户名需为3–50位字母、数字或下划线，暂不支持中文' : '填写格式不正确，请检查后重试';
  if (/SQL|Exception|java\.|org\.|stack trace/i.test(text)) return '服务暂时繁忙，请稍后重试';
  if (text === '用户名已存在') return '这个用户名已被使用，请换一个试试';
  const familiar: Record<string,string> = {
    '密码错误': '密码不正确，请检查后重新输入',
    '用户不存在': '没有找到这个账号，请检查登录名',
    '账号已被禁用': '当前账号暂时无法使用，请联系管理员',
    '认证服务暂不可用': '登录服务暂时不可用，请稍后重试'
  };
  if (familiar[text]) return familiar[text];
  return text.replace(/\b(username|oldPassword|newPassword|password|email|phone|captchaCode)\s*:/g, key => ({username:'用户名：',oldPassword:'当前密码：',newPassword:'新密码：',password:'密码：',email:'邮箱：',phone:'联系电话：',captchaCode:'验证码：'}[key.replace(/\s*:/,'') as 'username'] || '填写内容：'));
}
export function feedbackMessage(error: unknown, fallback: string): string {
  const e = error as { userMessage?: string; response?: { data?: { msg?: string } }; message?: string };
  return readableMessage(e?.userMessage || e?.response?.data?.msg, fallback);
}
