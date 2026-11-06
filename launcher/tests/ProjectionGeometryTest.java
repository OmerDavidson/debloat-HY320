package com.vibehome.launcher;

import java.util.Arrays;

/** Geometry contracts protect the picture from invalid or unexpectedly destructive draft shapes. */
public final class ProjectionGeometryTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void rejects(Runnable code){try{code.run();throw new AssertionError("Unsafe geometry accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        int[] baseline=ProjectionGeometry.parse("167,166,167,167,167,167,167,166");
        check(Arrays.equals(baseline,ProjectionGeometry.scale(baseline,100)),"Identity changes calibration");
        int[] trapezoid={70,90,180,220,110,130,60,100};
        int[] copy=trapezoid.clone(),small=ProjectionGeometry.scale(trapezoid,90);
        check(Arrays.equals(copy,trapezoid),"Scaling mutated the captured baseline");
        int[][] before=ProjectionGeometry.points(trapezoid),after=ProjectionGeometry.points(small);
        for(int corner=0;corner<4;corner++)for(int axis=0;axis<2;axis++){
            float expected=500+(before[corner][axis]-500)*0.9f;
            check(Math.abs(expected-after[corner][axis])<=0.51f,"Zoom discarded a corner correction");
        }
        check(!Arrays.equals(small,ProjectionGeometry.scale(new int[8],90)),"Trapezoid unexpectedly became a rectangle");
        rejects(()->ProjectionGeometry.parse("0,0,0,0,0,0,0"));
        rejects(()->ProjectionGeometry.parse("-1,0,0,0,0,0,0,0"));
        rejects(()->ProjectionGeometry.parse("0,0,0,0,0,0,451,0"));
        rejects(()->ProjectionGeometry.scale(new int[8],120)); // would crop outside the full panel.
        rejects(()->ProjectionGeometry.scale(baseline,79));
        System.out.println("Geometry contracts passed: identity, unchanged baseline, retained trapezoid, bounds and unsafe enlargement.");
    }
}
