// edit-structure.js
// 问卷结构编辑脚本（拆分独立文件 + 调试日志）
(function () {
    AppBoot.waitAPI(() => {
        if (typeof window.$ !== 'function' || typeof window.el !== 'function') {
            console.error('[edit-structure] $ 或 el 未定义');
            return;
        }
        const addBtn = document.getElementById('addQuestion');
        if (!addBtn) {
            console.error('[edit-structure] addQuestion 按钮未找到');
            return;
        }
        const surveyId = new URLSearchParams(location.search).get('id');
        if (!surveyId) {
            toast('缺少问卷 id', 'error');
            return;
        }
        console.debug('[edit-structure] init surveyId=', surveyId);
        let questions = [];
        const RULE_BLACKLIST = ['java.lang', 'Packages', 'java.io', 'java.nio', 'java.net', 'Runtime', 'Process', 'Thread', 'System.exit', 'Java.type'];

        function validateRule(code) {
            if (!code) return null;
            if (code.length > 1024) return '规则过长 (>1024)';
            const lower = code.toLowerCase();
            for (const k of RULE_BLACKLIST) {
                if (lower.includes(k.toLowerCase())) return '包含禁止片段: ' + k;
            }
            return null;
        }

        function ensureRule(q) {
            if (!q.score_rule) q.score_rule = {full_score: 0, rule_js_func: ''};
        }

        function render() {
            const box = $('#questions');
            if (!box) {
                console.error('[edit-structure] #questions 容器缺失');
                return;
            }
            box.innerHTML = '';
            if (questions.length === 0) {
                box.appendChild(el('div', {style: 'color:#888;padding:4px 0;'}, '暂无题目'));
            }
            questions.forEach((q, i) => {
                const card = el('div', {class: 'panel', style: 'background:#1e2430;margin:8px 0;padding:8px;'});
                card.appendChild(el('div', {style: 'display:flex;gap:8px;align-items:center;'},
                    el('span', {style: 'font-weight:bold;'}, 'Q' + (i + 1)),
                    (function () {
                        const inp = el('input', {value: q.question_content || '', style: 'flex:1;'});
                        inp.oninput = () => {
                            q.question_content = inp.value;
                        };
                        return inp;
                    })(),
                    (function () {
                        const sel = el('select');
                        sel.innerHTML = '<option value="1">单选</option><option value="3">多选</option><option value="2">文本</option>';
                        sel.value = q.question_type || 1;
                        sel.onchange = () => {
                            q.question_type = parseInt(sel.value);
                            if (q.question_type === 2) {
                                q.options = [];
                            }
                            render();
                        };
                        return sel;
                    })(),
                    el('button', {class: 'secondary'}, '删除')
                ));
                card.querySelector('button.secondary').onclick = () => {
                    questions.splice(i, 1);
                    render();
                };
                if (q.question_type === 1 || q.question_type === 3) {
                    const optWrap = el('div', {style: 'margin:6px 0 4px 26px;display:flex;flex-direction:column;gap:4px;'});
                    (q.options || []).forEach((op, oi) => {
                        const labelFallback = String.fromCharCode(65 + oi);
                        const row = el('div', {style: 'display:flex;gap:6px;align-items:center;'},
                            el('span', {style: 'width:26px;color:#999;'}, labelFallback),
                            (function () {
                                const il = el('input', {
                                    value: op.label || '',
                                    placeholder: '标签(A/B/...)',
                                    style: 'width:80px;'
                                });
                                il.oninput = () => {
                                    op.label = il.value;
                                };
                                return il;
                            })(),
                            (function () {
                                const ic = el('input', {
                                    value: op.content || '',
                                    placeholder: '内容',
                                    style: 'flex:1;'
                                });
                                ic.oninput = () => {
                                    op.content = ic.value;
                                };
                                return ic;
                            })(),
                            (function () {
                                const btn = el('button', {
                                    class: 'secondary',
                                    type: 'button',
                                    style: 'padding:2px 6px;'
                                }, '设为正确');
                                btn.onclick = () => {
                                    ensureRule(q);
                                    if (!q.score_rule.full_score || isNaN(q.score_rule.full_score)) q.score_rule.full_score = 5;
                                    const lbl = (op.label && op.label.trim()) || labelFallback;
                                    q.score_rule.rule_js_func = "function score(answer){ return answer==='" + lbl + "'?" + q.score_rule.full_score + ":0; }";
                                    render();
                                };
                                return btn;
                            })(),
                            (function () {
                                const mark = el('span', {style: 'font-size:12px;color:#9ee37d;'}, '');
                                try {
                                    const cur = deriveCorrectLabel(q);
                                    const thisLbl = (op.label && op.label.trim()) || labelFallback;
                                    if (cur && cur === thisLbl) {
                                        mark.textContent = '✓ 正确';
                                    }
                                } catch (_) {
                                }
                                return mark;
                            })(),
                            (function () {
                                const del = el('button', {class: 'secondary', style: 'padding:2px 6px;'}, '×');
                                del.onclick = () => {
                                    q.options.splice(oi, 1);
                                    render();
                                };
                                return del;
                            })()
                        );
                        optWrap.appendChild(row);
                    });
                    const addOpt = el('button', {class: 'secondary', style: 'width:120px;margin-top:4px;'}, '+ 选项');
                    addOpt.onclick = () => {
                        q.options.push({label: '', content: ''});
                        render();
                    };
                    optWrap.appendChild(addOpt);
                    card.appendChild(optWrap);
                }
                const ruleBox = el('div', {style: 'margin-left:26px;margin-top:4px;'});
                ruleBox.appendChild(el('div', {style: 'font-size:12px;color:#6aa1ff;display:flex;align-items:center;gap:8px;flex-wrap:wrap;'},
                    '评分规则 (可选)：full_score + js 表达式 / 函数',
                    (function () {
                        const btn = el('button', {
                            class: 'secondary',
                            type: 'button',
                            style: 'padding:2px 8px;'
                        }, '用模板生成');
                        btn.onclick = () => {
                            ensureRule(q);
                            // 默认满分 5
                            q.score_rule.full_score = q.score_rule.full_score || 5;
                            if (q.question_type === 1) {
                                // 单选：默认使用首个选项标签；无则用 A
                                const firstLbl = ((q.options && q.options[0] && q.options[0].label) || 'A').toString().trim() || 'A';
                                q.score_rule.rule_js_func = "function score(answer){ return answer==='" + firstLbl + "'?" + q.score_rule.full_score + ":0; }";
                            } else {
                                // 文本：完全匹配 '正确答案' 得满分，否则 0
                                q.score_rule.rule_js_func = "function score(answer){ return String(answer).trim()==='正确答案'?" + q.score_rule.full_score + ":0; }";
                            }
                            render();
                        };
                        return btn;
                    })()
                    (function () {
                        const btn = el('button', {
                            class: 'secondary',
                            type: 'button',
                            style: 'padding:2px 8px;'
                        }, '更多模板');
                        btn.onclick = () => {
                            const type = prompt('选择模板:\n1. 同时包含（多选）\n2. 至少包含其一（多选）\n3. 文本包含\n输入 1/2/3');
                            if (!type) return;
                            ensureRule(q);
                            if (!q.score_rule.full_score || isNaN(q.score_rule.full_score)) q.score_rule.full_score = 5;
                            if (type === '1' || type === '2') {
                                const labels = prompt('输入正确选项标签，用逗号分隔（例如 A,C 或 a,c）');
                                if (!labels) return;
                                const arr = labels.split(',').map(s => s.trim().toUpperCase()).filter(Boolean);
                                if (arr.length === 0) {
                                    toast('未提供有效标签', 'error');
                                    return;
                                }
                                if (type === '1') {
                                    // all-of
                                    q.score_rule.rule_js_func = "function score(answer){ var arr=String(answer).split(','); var upper=arr.map(function(x){return String(x).toUpperCase().trim();}); var ok=" + JSON.stringify(arr) + ".every(function(x){return upper.indexOf(x)>=0;}); return ok?" + q.score_rule.full_score + ":0; }";
                                } else {
                                    // any-of
                                    q.score_rule.rule_js_func = "function score(answer){ var arr=String(answer).split(','); var upper=arr.map(function(x){return String(x).toUpperCase().trim();}); var ok=" + JSON.stringify(arr) + ".some(function(x){return upper.indexOf(x)>=0;}); return ok?" + q.score_rule.full_score + ":0; }";
                                }
                            } else if (type === '3') {
                                const kw = prompt('请输入需包含的关键字（大小写敏感）');
                                if (!kw) return;
                                q.score_rule.rule_js_func = "function score(answer){ return String(answer).indexOf('" + kw.replace(/'/g, "\\'") + "')>=0?" + q.score_rule.full_score + ":0; }";
                            } else {
                                toast('未知选项', 'error');
                                return;
                            }
                            render();
                        };
                        return btn;
                    })()
                ));
                const fs = el('input', {
                    type: 'number',
                    value: q.score_rule?.full_score ?? '',
                    placeholder: '满分',
                    style: 'width:80px;margin-right:6px;'
                });
                fs.oninput = () => {
                    ensureRule(q);
                    q.score_rule.full_score = parseFloat(fs.value) || 0;
                };
                const ta = el('textarea', {
                    rows: 2,
                    style: 'width:100%;',
                    placeholder: "示例: answer==='A'?5:0"
                }, q.score_rule?.rule_js_func || '');
                ta.oninput = () => {
                    if (ta.value.trim() === '') {
                        q.score_rule = null;
                    } else {
                        ensureRule(q);
                        q.score_rule.rule_js_func = ta.value;
                    }
                };
                ruleBox.appendChild(el('div', {}, fs));
                ruleBox.appendChild(ta);
                // 预览评分区域
                const pv = el('div', {style: 'margin-top:6px;display:flex;align-items:center;gap:8px;flex-wrap:wrap;'});
                pv.appendChild(el('span', {class: 'muted', style: 'font-size:12px;'}, '预览评分：'));
                let ansInput;
                if (q.question_type === 1) {
                    const sel = el('select', {style: 'min-width:120px;'});
                    const opts = (q.options || []);
                    if (opts.length === 0) {
                        sel.innerHTML = '<option value="">(无选项)</option>';
                    } else {
                        sel.innerHTML = opts.map(o => `<option value="${(o.label || '').toString().trim()}">${(o.label || '')}</option>`).join('');
                    }
                    ansInput = sel;
                } else if (q.question_type === 3) {
                    ansInput = el('input', {placeholder: '输入示例: A,B', style: 'min-width:200px;'});
                } else {
                    ansInput = el('input', {placeholder: '输入测试答案', style: 'min-width:200px;'});
                }
                const btnTry = el('button', {class: 'secondary', type: 'button', style: 'padding:4px 10px;'}, '试算');
                const out = el('span', {style: 'font-size:13px;color:#9ee37d;'});
                btnTry.onclick = () => {
                    const code = q.score_rule?.rule_js_func || '';
                    const err = validateRule(code);
                    if (err) {
                        toast('规则非法: ' + err, 'error');
                        return;
                    }
                    const ans = (ansInput.tagName === 'SELECT') ? ansInput.value : ansInput.value;
                    const full = parseFloat(q.score_rule?.full_score) || 0;
                    const val = previewEval(code, ans, isNaN(full) ? 0 : full);
                    if (isNaN(val)) {
                        out.textContent = '结果: NaN (请检查返回值)';
                        out.style.color = '#fca5a5';
                    } else {
                        out.textContent = '预计得分: ' + val + (full > 0 ? (' / ' + full) : '');
                        out.style.color = '#9ee37d';
                    }
                };
                pv.appendChild(ansInput);
                pv.appendChild(btnTry);
                pv.appendChild(out);
                pv.appendChild(el('span', {
                    class: 'muted',
                    style: 'font-size:12px;'
                }, '（本地预估，保存后以后端计算为准）'));
                ruleBox.appendChild(pv);
                card.appendChild(ruleBox);
                box.appendChild(card);
            });
        }

        addBtn.addEventListener('click', () => {
            console.debug('[edit-structure] add question click');
            questions.push({question_content: '', question_type: 1, options: [], score_rule: null});
            render();
        });
        document.getElementById('saveStructure').addEventListener('click', async () => {
            try {
                for (const q of questions) {
                    if (!q.question_content.trim()) {
                        toast('存在空题目内容', 'error');
                        return;
                    }
                    if (q.question_type === 1 || q.question_type === 3) {
                        q.options = q.options.filter(o => o.label || o.content);
                        if (q.options.length < 2) {
                            toast('选择题需至少两个选项', 'error');
                            return;
                        }
                    }
                }
                for (const q of questions) {
                    if (q.score_rule && q.score_rule.rule_js_func) {
                        const err = validateRule(q.score_rule.rule_js_func);
                        if (err) {
                            toast('题目 ' + q.question_content + ' 规则非法: ' + err, 'error');
                            return;
                        }
                    }
                }
                const payload = {
                    questions: questions.map(q => ({
                        question_content: q.question_content,
                        question_type: q.question_type,
                        options: (q.question_type === 1 || q.question_type === 3) ? q.options : undefined,
                        score_rule: q.score_rule ? {
                            full_score: q.score_rule.full_score,
                            rule_js_func: q.score_rule.rule_js_func
                        } : undefined
                    }))
                };
                const res = await fetch('/api/surveys/' + surveyId + '/structure', {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json', 'X-Auth-Token': API.token()},
                    body: JSON.stringify(payload)
                }).then(r => r.json());
                if (res && res.error) {
                    throw res;
                }
                toast('结构已保存');
            } catch (e) {
                toast('保存失败 ' + (e.message || e.error || ''), 'error');
            }
        });
        document.getElementById('saveMeta').addEventListener('click', async () => {
            try {
                await API.updateSurvey(surveyId, {
                    title: $('#surveyTitle').value.trim(),
                    description: $('#surveyDesc').value.trim()
                });
                toast('基本信息已保存');
            } catch (e) {
                toast('保存失败', 'error');
            }
        });

        async function load() {
            try {
                const s = await API.getSurvey(surveyId);
                $('#surveyTitle').value = s.title || '';
                $('#surveyDesc').value = s.description || '';
                questions = (s.questions || []).map(q => ({
                    question_content: q.questionContent || q.question_content || q.content || q.question_content,
                    question_type: q.questionType || q.question_type || 1,
                    options: (q.options || []).map(o => ({
                        label: o.optionLabel || o.label,
                        content: o.optionContent || o.content
                    })),
                    score_rule: q.scoreRuleId ? {
                        full_score: q.fullScore || q.full_score || 0,
                        rule_js_func: q.ruleJsFunc || q.rule_js_func || ''
                    } : null
                }));
                render();
                console.debug('[edit-structure] loaded survey, questions=', questions.length);
            } catch (e) {
                toast('加载失败', 'error');
            }
        }

        function deriveCorrectLabel(q) {
            try {
                const code = q && q.score_rule && q.score_rule.rule_js_func || '';
                const m = code.match(/answer\s*===\s*'([A-Za-z])'/);
                return m ? m[1] : null;
            } catch (_) {
                return null;
            }
        }

        // 本地预览评分：支持 function score(answer){...} 或表达式 answer==='A'?5:0
        function previewEval(src, answer, fullScore) {
            if (!src || !src.trim()) return 0;
            const code = src.trim();
            let fn;
            try {
                if (/^function\s+score\s*\(/.test(code)) {
                    // 括号包裹以 eval 成为函数对象
                    /* eslint no-eval: 0 */
                    fn = eval('(' + code + ')');
                } else if (code.startsWith('function ')) {
                    // 其他命名函数，不做复杂解析，直接失败为 0
                    return 0;
                } else {
                    fn = new Function('answer', 'try { return (' + code + '); } catch(e){ return 0; }');
                }
                let v = Number(fn(answer));
                if (!isFinite(v)) v = 0;
                if (fullScore && fullScore > 0 && v > fullScore) v = fullScore;
                return v;
            } catch (e) {
                return 0;
            }
        }

        load();
        if (window.API && typeof API.injectUserNav === 'function') {
            API.injectUserNav();
        }
    });
})();
