import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Cria contas/horários exclusivos; não apaga dados nem usa usuários existentes.
const site = process.env.E2E_SITE_URL || 'http://127.0.0.1:5173';
const servidor = process.env.E2E_API_URL || 'http://localhost:8081';
const senha = 'TesteAgenda123!';
const prefixo = 's3-' + Date.now();
const email = nome => `${prefixo}-${nome}@example.com`;

async function requisitar(caminho, token, corpo) {
  return fetch(servidor + caminho, {
    method: corpo ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: corpo ? JSON.stringify(corpo) : undefined,
  });
}
async function cadastrar(page, nome, tipo) {
  await page.getByRole('link', { name: 'Criar conta', exact: true }).click();
  await page.getByLabel(tipo === 'professor' ? 'Professor' : 'Aluno', { exact: true }).check();
  await page.getByLabel('Nome', { exact: true }).fill(nome);
  await page.getByLabel('E-mail', { exact: true }).fill(email(nome));
  await page.getByLabel('Senha', { exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Criar conta', exact: true }).click();
}
async function escolherSemana(page) {
  await page.getByRole('button', { name: /Próxima semana/ }).click();
  await page.getByRole('button', { name: /12:00/ }).waitFor();
}

test('3.4 — entregas das semanas 2 e 3 com API e navegador reais', { timeout: 180_000 }, async t => {
  const browser = await chromium.launch({ headless: true, channel: 'chromium' });
  t.after(() => browser.close());
  const erros = [];
  async function sessao() {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await context.newPage();
    page.setDefaultTimeout(15_000);
    page.on('pageerror', e => erros.push(e.message));
    await page.goto(site + '/#/login');
    return page;
  }
  const professor = await sessao(), aluno = await sessao(), segundo = await sessao();
  let link, horarioDisputa, tokenProfessor, tokenAluno;

  await t.test('cadastro de professor redireciona para horários e guarda token', async () => {
    await cadastrar(professor, 'professor', 'professor');
    await professor.getByRole('heading', { name: 'Meus horários', exact: true }).waitFor();
    tokenProfessor = await professor.evaluate(() => localStorage.getItem('token'));
    assert.ok(tokenProfessor);
    link = await professor.locator('input[readonly]').inputValue();
    assert.match(link, /#\/agendamento\/[a-f0-9]{24}$/);
  });
  await t.test('professor cria online/presencial, remove horário e mantém após recarregar', async () => {
    await professor.getByRole('button', { name: '→', exact: true }).click();
    const linha = h => professor.getByRole('row').filter({ has: professor.getByRole('cell', { name: h, exact: true }) });
    async function criar(h, modalidade) {
      await professor.getByLabel(modalidade, { exact: true }).check();
      const resposta = professor.waitForResponse(r => r.url().endsWith('/horarios') && r.request().method() === 'POST');
      await linha(h).locator('td').nth(1).getByRole('button').click();
      const r = await resposta; assert.equal(r.status(), 201);
      await linha(h).getByRole('button', { name: modalidade, exact: true }).waitFor();
      return (await r.json()).id;
    }
    await criar('10:00', 'Online');
    horarioDisputa = await criar('12:00', 'Presencial');
    await criar('14:00', 'Online');
    const removido = professor.waitForResponse(r => r.request().method() === 'DELETE');
    await linha('14:00').getByRole('button', { name: 'Online', exact: true }).click();
    assert.equal((await removido).status(), 204);
    await professor.reload();
    await professor.getByRole('button', { name: '→', exact: true }).click();
    await linha('10:00').getByRole('button', { name: 'Online', exact: true }).waitFor();
    await linha('12:00').getByRole('button', { name: 'Presencial', exact: true }).waitFor();
  });
  await t.test('link do professor é preservado durante cadastro de aluno', async () => {
    await aluno.goto(link);
    await cadastrar(aluno, 'aluno', 'aluno');
    await aluno.getByRole('heading', { name: 'Agendar aula', exact: true }).waitFor();
    assert.equal(aluno.url(), link);
    tokenAluno = await aluno.evaluate(() => localStorage.getItem('token'));
    assert.ok(tokenAluno);
    await escolherSemana(aluno);
  });
  await t.test('aluno cancela seleção antes de confirmar; reserva e horário desaparece', async () => {
    await aluno.getByRole('button', { name: /10:00/ }).click();
    await aluno.getByRole('button', { name: 'Cancelar seleção', exact: true }).click();
    assert.equal(await aluno.getByRole('button', { name: 'Confirmar agendamento', exact: true }).count(), 0);
    await aluno.getByRole('button', { name: /10:00/ }).click();
    await aluno.getByRole('button', { name: 'Confirmar agendamento', exact: true }).click();
    await aluno.getByText('Aula agendada com sucesso!', { exact: true }).waitFor();
    await aluno.getByRole('button', { name: /12:00/ }).waitFor();
    assert.equal(await aluno.getByRole('button', { name: /10:00/ }).count(), 0);
  });
  await t.test('duas sessões disputam o mesmo horário: apenas uma confirma', async () => {
    await segundo.goto(link); await cadastrar(segundo, 'segundo', 'aluno');
    await segundo.getByRole('heading', { name: 'Agendar aula', exact: true }).waitFor();
    await escolherSemana(segundo);
    await aluno.getByRole('button', { name: /12:00/ }).click();
    await segundo.getByRole('button', { name: /12:00/ }).click();
    await aluno.getByRole('button', { name: 'Confirmar agendamento', exact: true }).click();
    await aluno.getByText('Aula agendada com sucesso!', { exact: true }).waitFor();
    await segundo.getByRole('button', { name: 'Confirmar agendamento', exact: true }).click();
    await segundo.getByRole('alert').getByText('Esse horário acabou de ser ocupado.', { exact: false }).waitFor();
    await segundo.getByText('Não há horários livres nesta semana.', { exact: false }).waitFor();
    assert.equal((await requisitar('/aulas', tokenAluno, { horarioId: horarioDisputa })).status, 409);
  });
  await t.test('reservas aparecem como ocupadas na agenda do professor', async () => {
    await professor.reload();
    await professor.getByRole('button', { name: '→', exact: true }).click();
    await professor.getByText('Reservado', { exact: true }).first().waitFor();
    await professor.waitForFunction(() => [...document.querySelectorAll('td div')].filter(e => e.textContent === 'Reservado').length === 2, null, { timeout: 5000 });
    assert.equal(await professor.locator('td').getByText('Reservado', { exact: true }).count(), 2);
  });
  await t.test('sem token e perfis errados não alteram a agenda', async () => {
    assert.equal((await requisitar('/horarios?de=2026-01-01&ate=2026-12-31')).status, 401);
    assert.equal((await requisitar('/horarios?de=2026-01-01&ate=2026-12-31', tokenAluno)).status, 403);
    assert.equal((await requisitar('/aulas', tokenProfessor, { horarioId: horarioDisputa })).status, 403);
    await aluno.goto(site + '/#/horarios');
    await aluno.getByRole('heading', { name: 'Agendar aula', exact: true }).waitFor();
    assert.notEqual(new URL(aluno.url()).hash, '#/horarios');
  });
  await t.test('logout, senha errada e login válido', async () => {
    await aluno.getByRole('button', { name: 'Sair', exact: true }).click();
    await aluno.getByLabel('E-mail', { exact: true }).fill(email('aluno'));
    await aluno.getByLabel('Senha', { exact: true }).fill('SenhaIncorreta');
    const falha = aluno.waitForResponse(r => r.url().endsWith('/login'));
    await aluno.getByRole('button', { name: 'Entrar', exact: true }).click();
    assert.equal((await falha).status(), 401);
    assert.equal(await aluno.evaluate(() => localStorage.getItem('token')), null);
    await aluno.getByLabel('Senha', { exact: true }).fill(senha);
    await aluno.getByRole('button', { name: 'Entrar', exact: true }).click();
    await aluno.getByRole('heading', { name: 'Agendar aula', exact: true }).waitFor();
  });
  await t.test('e-mail duplicado e senha curta recusados no cadastro', async () => {
    const novo = await sessao();
    await novo.getByRole('link', { name: 'Criar conta', exact: true }).click();
    await novo.getByLabel('Nome', { exact: true }).fill('Duplicado');
    await novo.getByLabel('E-mail', { exact: true }).fill(email('aluno'));
    await novo.getByLabel('Senha', { exact: true }).fill('123');
    await novo.getByRole('button', { name: 'Criar conta', exact: true }).click();
    await novo.getByText('A senha precisa ter pelo menos 6 caracteres.', { exact: true }).waitFor();
    await novo.getByLabel('Senha', { exact: true }).fill(senha);
    const duplicado = novo.waitForResponse(r => r.url().endsWith('/cadastro'));
    await novo.getByRole('button', { name: 'Criar conta', exact: true }).click();
    assert.equal((await duplicado).status(), 409);
    assert.equal(await novo.evaluate(() => localStorage.getItem('token')), null);
  });
  assert.deepEqual(erros, [], 'sem exceções JavaScript no navegador');
});
