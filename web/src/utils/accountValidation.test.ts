import test from 'node:test';
import assert from 'node:assert/strict';
import { registrationErrors, passwordError, usernameError } from './accountValidation.ts';
import { readableMessage, feedbackMessage } from './feedback.ts';

test('注册规则提前解释中文用户名、长度、重复密码及选填联系方式', () => {
  const form = { username: '测试用户', password: 'testPassword9', password2: 'testPassword9', email: '', phone: '' };
  assert.match(registrationErrors(form).username, /暂不支持中文/);
  assert.equal(usernameError('valid_user9'), '');
  assert.notEqual(usernameError('ab'), '');
  assert.notEqual(usernameError('a b'), '');
  assert.equal(registrationErrors({...form,username:'valid_user9'}).email,'');
  assert.match(registrationErrors({...form,password2:'different9'}).password2,/不一致/);
  assert.match(registrationErrors({...form,email:'invalid'}).email,/邮箱格式/);
  assert.match(passwordError('新'.repeat(25)),/过长/);
  assert.equal(passwordError('a'.repeat(72)),'');
});

test('旧服务错误、内部异常与网络反馈保留可执行提示而不暴露技术信息', () => {
  assert.match(readableMessage('username: 需要匹配正则表达式 [a-zA-Z0-9_]{3,50}'),/用户名需为/);
  assert.doesNotMatch(readableMessage('username: 需要匹配正则表达式 [a-zA-Z0-9_]{3,50}'),/正则|username|\[a-z/);
  assert.match(readableMessage('java.sql.SQLException: hidden database details'),/稍后重试/);
  assert.match(readableMessage('用户名已存在'),/换一个/);
  assert.equal(feedbackMessage({userMessage:'网络连接不稳定，请检查网络后重试'},'失败'),'网络连接不稳定，请检查网络后重试');
});
