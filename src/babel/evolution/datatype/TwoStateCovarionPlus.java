package babel.evolution.datatype;

import beast.base.core.Description;
import beast.base.evolution.datatype.DataType;
import beast.base.evolution.datatype.DataType.Base;

@Description("Datatype for two state covarion sequences")
public class TwoStateCovarionPlus extends Base {
    int[][] x = {
            {0, 2, 4},  // 0
            {1, 3, 5},  // 1
            {0},  // a
            {1},  // b
            {2},  // c
            {3},  // d
            {4},  // e
            {5},  // f
            {0, 1, 2, 3, 4, 5},  // -
            {0, 1, 2, 3, 4, 5},  // ?
    };

    public TwoStateCovarionPlus() {
        stateCount = 6;
        mapCodeToStateSet = x;
        codeLength = 1;
        codeMap = "01abcdef" + GAP_CHAR + MISSING_CHAR;
    }

    @Override
    public String getTypeDescription() {
        return "twoStateCovarionPlus";
    }

    @Override
    public char getChar(int state) {
        return codeMap.charAt(state);
    }
}
