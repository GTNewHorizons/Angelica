package jss.util;

public class DirectionHelper {

    public static final int[] orderedDirections = {
        0, 1, 2, 3, 4, 5
    };

    public static final int[] oppositeDirections = {
        1, 0, 3, 2, 5, 4
    };

    public static final int[] yDirectionalIncrease = {
        -1, 1, -0, 0, -0, 0
    };

    public static final int[] zDirectionalIncrease = {
        -0, 0, -1, 1, -0, 0
    };

    public static final int[] xDirectionalIncrease = {
        -0, 0, -0, 0, -1, 1
    };

    public static final int[] relativeADirections = {
        2, 3, 4, 5, 0, 1
    };

    public static final int[] relativeBDirections = {
        3, 2, 5, 4, 1, 0
    };

    public static final int[] relativeCDirections = {
        4, 5, 0, 1, 2, 3
    };

    public static final int[] relativeDDirections = {
        5, 4, 1, 0, 3, 2
    };

}
