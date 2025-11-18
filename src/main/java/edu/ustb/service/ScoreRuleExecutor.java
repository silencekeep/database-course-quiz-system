package edu.ustb.service;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 改进版评分规则执行：
 * 1. 支持“表达式”与“完整函数”两种形式；表达式会被包装为函数。
 * 2. 为每个线程维护独立 ScriptEngine，避免函数名覆盖竞态。
 * 3. 缓存每个 ruleId 已编译（已注入 Engine）的函数；源变更时自动重新编译。
 * 4. 支持最大分(fullScore)截断。
 */
public class ScoreRuleExecutor {
    private static final ScriptEngineManager MANAGER = new ScriptEngineManager();
    private final ThreadLocal<ScriptEngine> engines = ThreadLocal.withInitial(() -> MANAGER.getEngineByName("nashorn"));
    // 每线程缓存：ruleId -> 已加载源码
    private final ThreadLocal<Map<Integer, String>> loadedSource = ThreadLocal.withInitial(ConcurrentHashMap::new);
    private static final int MAX_SOURCE_LEN = 1024; // 源长度限制
    private static final String[] BLACKLIST = {"Java.type","Packages","java.lang","java.io","java.nio","System","Runtime","Thread","process","exec"};
    private static final long TIMEOUT_MS = 50; // 简易执行超时时间片（非严格）

    /** 旧接口兼容：无 ruleId / fullScore 信息 */
    public double eval(String jsFuncOrExpr, String answer){
        return eval(0, jsFuncOrExpr, answer, null);
    }

    /**
     * @param ruleId 规则ID（用于缓存 / 唯一函数名）
     * @param jsFuncOrExpr 可能是完整函数 (包含"function score(") 或 纯表达式 (如 answer==='A'?5:0)
     * @param answer 用户答案
    * @param fullScore 最大得分（>0 时生效；<=0 或 null 表示不限制）
     */
    public double eval(int ruleId, String jsFuncOrExpr, String answer, Float fullScore){
    if(jsFuncOrExpr == null || jsFuncOrExpr.isBlank()) return 0d;
    if(jsFuncOrExpr.length() > MAX_SOURCE_LEN) return 0d;
    for(String bad : BLACKLIST){ if(jsFuncOrExpr.contains(bad)) return 0d; }
        String trimmed = jsFuncOrExpr.trim();
        String fnName = "score_" + ruleId;
        // 构造标准函数源码
        String finalFuncSource = getString(trimmed, fnName);
        ScriptEngine engine = engines.get();
        Map<Integer,String> cache = loadedSource.get();
        // 若未加载或源码变更 => 重新 eval
        String prev = cache.get(ruleId);
        if(prev == null || !prev.equals(finalFuncSource)) {
            try {
                engine.eval(finalFuncSource);
                cache.put(ruleId, finalFuncSource);
            } catch(Exception e){
                return 0d; // 编译失败
            }
        }
        long start = System.currentTimeMillis();
        try {
            Object result = ((Invocable)engine).invokeFunction(fnName, answer);
            if(System.currentTimeMillis()-start > TIMEOUT_MS) return 0d; // 超时判定
            double val = (result instanceof Number)? ((Number)result).doubleValue(): 0d;
            if(fullScore != null && fullScore > 0f) {
                if(val > fullScore) val = fullScore;
            }
            return val;
        } catch(Exception e){
            return 0d;
        }
    }

    private static String getString(String trimmed, String fnName) {
        String finalFuncSource;
        if(trimmed.startsWith("function score(")) {
            // 重命名函数为唯一名称，避免不同规则覆盖
            finalFuncSource = trimmed.replaceFirst("function\\s+score\\s*\\(", "function "+ fnName +"(");
        } else if(trimmed.startsWith("function ")) {
            // 已是其它命名函数，直接使用（但仍建议统一）
            finalFuncSource = trimmed;
        } else {
            // 视为表达式，包装 try-catch
            finalFuncSource = "function "+ fnName +"(answer){ try { return ("+ trimmed +"); } catch(e){ return 0; } }";
        }
        return finalFuncSource;
    }
}