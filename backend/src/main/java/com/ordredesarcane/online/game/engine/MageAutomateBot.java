package com.ordredesarcane.online.game.engine;

import com.ordredesarcane.online.game.dto.GameActionCommand;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.GameRoom;
import com.ordredesarcane.online.game.model.PlayerState;
import org.springframework.stereotype.Component;

/**
 * Authoritative AI opponent ("Mage Automate") operating strictly on Fog-of-War safe public board
 * state and its own private hand/resources.
 * <p>
 * Delegates tactical sequencing, combo evaluation, lethal resolution, modal choices, reactions,
 * and mulligan/discard strategy to {@link BotTacticalPlanner}.
 */
@Component
public class MageAutomateBot {

    private final BotTacticalPlanner planner;

    public MageAutomateBot(
            BoardRuleEvaluator evaluator,
            AbilityInterpreter abilities,
            CombatResolver combatResolver,
            FogOfWarProjector projector) {
        this.planner = new BotTacticalPlanner(evaluator, abilities, combatResolver, projector);
    }

    public GameActionCommand chooseMulliganAction(GameRoom room, PlayerState bot) {
        return planner.chooseMulliganAction(room, bot, resolveDifficulty(bot));
    }

    public GameActionCommand chooseReactionAction(GameRoom room, PlayerState bot) {
        return planner.chooseReactionAction(room, bot, resolveDifficulty(bot));
    }

    public GameActionCommand chooseModalAction(GameRoom room, PlayerState bot) {
        return planner.chooseModalAction(room, bot, resolveDifficulty(bot));
    }

    public GameActionCommand chooseDiscardAction(GameRoom room, PlayerState bot) {
        return planner.chooseDiscardAction(room, bot, resolveDifficulty(bot));
    }

    public GameActionCommand chooseNextTurnAction(GameRoom room, PlayerState bot) {
        return planner.chooseNextTurnAction(room, bot, resolveDifficulty(bot));
    }

    private AiDifficulty resolveDifficulty(PlayerState bot) {
        return AiDifficulty.resolveOrDefault(bot.getAiDifficulty());
    }
}
