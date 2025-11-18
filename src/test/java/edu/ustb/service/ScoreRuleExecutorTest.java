package edu.ustb.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ScoreRuleExecutorTest {
    @Test
    void testReturnNumber() {
        ScoreRuleExecutor ex = new ScoreRuleExecutor();
        double v = ex.eval("function score(answer){ return answer==='X'?3.5:0; }", "X");
        assertEquals(3.5d, v, 0.0001);
    }

    @Test
    void testReturnNonNumberTreatAs0() {
        ScoreRuleExecutor ex = new ScoreRuleExecutor();
        double v = ex.eval("function score(answer){ return 'abc'; }", "Y");
        assertEquals(0d, v, 0.0001);
    }

    @Test
    void testScriptThrowsReturn0() {
        ScoreRuleExecutor ex = new ScoreRuleExecutor();
        double v = ex.eval("function score(answer){ throw new Error('boom'); }", "Z");
        assertEquals(0d, v, 0.0001);
    }
}
