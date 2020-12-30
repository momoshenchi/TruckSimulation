package examples.fuzzytruckswing;

import nrc.fuzzy.*;

import java.awt.*;
import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.text.*;
import java.util.Arrays;

// This is the base class for truck simulator. It handles setting up
// the initial data and running the algorithm.

public class TruckSimulation extends Thread
{
    FuzzyTruckJApplet parent;

    public static final int PAUSE = 0;
    public static final int STEP = 1;
    public static final int RUN = 2;

    public static final int ROWS = 7;
    public static final int COLUMNS = 5;

    public static final double PIBY180 = Math.PI / 180;   //1degree ==

    boolean pause = true, finished = false, truck_disabled = false;
    // initial state of simulation is paused
    int msg = PAUSE;

    static final NumberFormat nf = NumberFormat.getNumberInstance();

    // some things to support threads for updating GUI
    String statusText = "";
    SetStatusTextThread sstThread = new SetStatusTextThread();
    Color conclusionColor;         // color of the conclusion button background
    int conclusionI, conclusionJ;  // index values into the conclusion button matrix


    // X, Y and Phi hold the starting state for the truck
    double X = 50, Y = 50, Phi = 90;
    // Xt, Yt, Phit hold state of truck as simulation progresses
    double Xt = 50, Yt = 50, Phit = 90;
    double beforex = 0;
    double beforey = 0;
    // speed of truck
    double Speed = 1;
    // speed of simulation
    int SimSpeed = 1;

    // true when need to re-evaluate truck position
    boolean recompute = true;
    // true when tracing check box is set
    boolean tracingWanted = false;
    // true when tracing to be done during update of graphics (only set
    // to true when tracing check box is on and simulation is under way)
    boolean tracingOn = false;
    // iteration count during simulation
    int Iteration = 0;
    // true when we are to show the rule firings as the simulation proceeds
    boolean showRuleFirings;

    // The fuzzy definitions,terms correspond to sets
    static FuzzyVariable xpos;
    static FuzzyVariable obsxpos;
    static String xposTerms[] = {"LeftBig", "LeftMedium", "Centred", "RightMedium", "RightBig"};
    static FuzzySet xposFzsets[] = new FuzzySet[COLUMNS];
    static FuzzySet obsxposFzsets[] = new FuzzySet[COLUMNS];

    static FuzzyVariable phi;
    static FuzzyVariable obsphi;
    static String phiTerms[] = {"LargeBelow90", "MediumBelow90", "SmallBelow90", "At90", "SmallAbove90", "MediumAbove90", "LargeAbove90"};
    static String obsphiTerms[] = {"MediumBelow90", "SmallBelow90", "At90", "SmallAbove90", "MediumAbove90"};
    static FuzzySet phiFzSets[] = new FuzzySet[ROWS];
    static FuzzySet obsphiFzSets[] = new FuzzySet[5];

    static FuzzyVariable changePhi;
    static FuzzyVariable obschangePhi;
    static String changePhiTerms[] = {"NB", "NM", "NS", "ZE", "PS", "PM", "PB"};
    static FuzzySet changePhiFzSets[] = new FuzzySet[ROWS];
//    static FuzzySet obschangePhiFzSets[] = new FuzzySet[7];

    static FuzzyRule theRules[][] = new FuzzyRule[ROWS][COLUMNS]; // 1st index is phi, 2nd is xpos
    static FuzzyRule obstheRules[][] = new FuzzyRule[5][COLUMNS]; // 1st index is phi, 2nd is xpos

    // OFF_COLOR is normal color for rule matrix buttons
    private static final Color OFF_COLOR = new Color(204, 204, 204);
    // matrix of colors that show degreee of matching for a rule that fires
    private static final Color matchColors[] = new Color[256];
    private static final Color obsmatchColors[] = new Color[256];

    static
    {   // use colors from white to pink to deep red to show degree of rule matching
        int i;
        for (i = 0; i < 256; i++)
        {
            matchColors[i] = new Color(255, i, i);
            obsmatchColors[i] = new Color(i, 255, i);
        }

        // display 4 digits precision for angle changes
        nf.setMaximumFractionDigits(4);
        nf.setMinimumFractionDigits(4);
    }

    // default values for the rule conclusions matrix (Reset Rules button
    // resets to these values)
    public static final String DefaultConclusions[][] =
            {{"PS", "PM", "PM", "PB", "PB"},
                    {"NS", "PS", "PM", "PB", "PB"},
                    {"NM", "NS", "PS", "PM", "PB"},
                    {"NM", "NM", "ZE", "PM", "PM"},
                    {"NB", "NM", "NS", "PS", "PM"},
                    {"NB", "NB", "NM", "NS", "PS"},
                    {"NB", "NB", "NM", "NM", "NS"}};

    // current values for the rule conclusions
    public static String CurrentConclusions[][] =
            {{"PS", "PM", "PM", "PB", "PB"},
                    {"NS", "PS", "PM", "PB", "PB"},
                    {"NM", "NS", "PS", "PM", "PB"},
                    {"NM", "NM", "ZE", "PM", "PM"},
                    {"NB", "NM", "NS", "PS", "PM"},
                    {"NB", "NB", "NM", "NS", "PS"},
                    {"NB", "NB", "NM", "NM", "NS"}};

    public static String obsCurrentConclusions[][] =
            {
                    {"PB", "PB", "NS", "NS", "NS"},
                    {"PM", "PM", "NM", "NS", "NS"},
                    {"PS", "PM", "PB", "NM", "NS"},
                    {"PS", "PS", "PM", "NM", "NM"},
                    {"PS", "PS", "PS", "NB", "NB"},
            };

    // Set up the data in a constructor
    public TruckSimulation(FuzzyTruckJApplet p)
    {
        parent = p;
        int i, j;

        // define the fuzzyVariables and terms
        // define the fuzzy input values (curentXpos and currentPhi)
        // define the fuzzy output value (changePhi)
        try
        {
            // data from Kosko's book.
            phiFzSets[0] = new RFuzzySet(-45.0, 10.0, new RightLinearFunction());
            phiFzSets[1] = new TriangleFuzzySet(-10.0, 25.0, 60.0);
            phiFzSets[2] = new TriangleFuzzySet(50.0, 70.0, 90.0);
            phiFzSets[3] = new TriangleFuzzySet(80.0, 90.0, 100.0);
            phiFzSets[4] = new TriangleFuzzySet(90.0, 110.0, 130.0);
            phiFzSets[5] = new TriangleFuzzySet(120.0, 155.0, 190.0);
            phiFzSets[6] = new LFuzzySet(170.0, 225.0, new LeftLinearFunction());
            obsphiFzSets[0] = new TriangleFuzzySet(0, 25.0, 60.0);
            obsphiFzSets[1] = new TriangleFuzzySet(50.0, 70.0, 90.0);
            obsphiFzSets[2] = new TriangleFuzzySet(80.0, 90.0, 100.0);
            obsphiFzSets[3] = new TriangleFuzzySet(90.0, 110.0, 130.0);
            obsphiFzSets[4] = new TriangleFuzzySet(120.0, 155.0, 180.0);

            changePhiFzSets[0] = new TriangleFuzzySet(-45.0, -30.0, -15.0);
            changePhiFzSets[1] = new TriangleFuzzySet(-25.0, -15.0, -5.0);
            changePhiFzSets[2] = new TriangleFuzzySet(-10.0, -5.0, 0.0);
            changePhiFzSets[3] = new TriangleFuzzySet(-5.0, 0.0, 5.0);
            changePhiFzSets[4] = new TriangleFuzzySet(0.0, 5.0, 10.0);
            changePhiFzSets[5] = new TriangleFuzzySet(5.0, 15.0, 25.0);
            changePhiFzSets[6] = new TriangleFuzzySet(15.0, 30.0, 45.0);

            xposFzsets[0] = new RFuzzySet(10.0, 35.0, new RightLinearFunction());
            xposFzsets[1] = new TriangleFuzzySet(30.0, 40.0, 50.0);
            xposFzsets[2] = new TriangleFuzzySet(45.0, 50.0, 55.0);
            xposFzsets[3] = new TriangleFuzzySet(50.0, 60.0, 70.0);
            xposFzsets[4] = new LFuzzySet(65.0, 90.0, new LeftLinearFunction());

            obsxposFzsets[0] = new TriangleFuzzySet(-0.50, -0.15, 0.10);
            obsxposFzsets[1] = new TriangleFuzzySet(0.10, 0.25, 0.40);
            obsxposFzsets[2] = new TriangleFuzzySet(0.40, 0.50, 0.65);
            obsxposFzsets[3] = new TriangleFuzzySet(0.65, 0.80, 0.95);
            obsxposFzsets[4] = new TriangleFuzzySet(0.95, 1.30, 1.60);
            // Input fuzzy variable for the truck's x coordinate position
            xpos = new FuzzyVariable("Xpos", 0, 100, "");
            for (i = 0; i < xposTerms.length; i++)
                xpos.addTerm(xposTerms[i], xposFzsets[i]);
            // Input fuzzy variable for the truck's angular position
            phi = new FuzzyVariable("Phi", -90, 270, "Degrees");
            for (i = 0; i < phiTerms.length; i++)
                phi.addTerm(phiTerms[i], phiFzSets[i]);
            // Output fuzzy variable for changing the truck's angular position
            changePhi = new FuzzyVariable("changePhi", -45.0, 45.0, "Degrees");
            for (i = 0; i < changePhiTerms.length; i++)
                changePhi.addTerm(changePhiTerms[i], changePhiFzSets[i]);

            //设置obs的set
            obsxpos = new FuzzyVariable("obsXpos", -0.65, 1.80, "");
            for (i = 0; i < xposTerms.length; i++)
                obsxpos.addTerm(xposTerms[i], obsxposFzsets[i]);
            // Input fuzzy variable for the truck's angular position
            obsphi = new FuzzyVariable("obsPhi", 0, 180, "Degrees");
            for (i = 0; i < obsphiTerms.length; i++)
                obsphi.addTerm(obsphiTerms[i], obsphiFzSets[i]);
            // Output fuzzy variable for changing the truck's angular position
            obschangePhi = new FuzzyVariable("obschangePhi", -45.0, 45.0, "Degrees");
            for (i = 0; i < changePhiTerms.length; i++)
                obschangePhi.addTerm(changePhiTerms[i], changePhiFzSets[i]);


            //define the fuzzy rules
            for (i = 0; i < phiTerms.length; i++)
            {
                for (j = 0; j < xposTerms.length; j++)
                {
                    theRules[i][j] = new FuzzyRule();
                    theRules[i][j].addAntecedent(new FuzzyValue(xpos, xposTerms[j]));
                    theRules[i][j].addAntecedent(new FuzzyValue(phi, phiTerms[i]));
                    theRules[i][j].addConclusion(new FuzzyValue(changePhi, CurrentConclusions[i][j]));
                }
            }

            for (i = 0; i < obsphiTerms.length; i++)
            {
                for (j = 0; j < xposTerms.length; j++)
                {
                    obstheRules[i][j] = new FuzzyRule();
                    obstheRules[i][j].addAntecedent(new FuzzyValue(obsxpos, xposTerms[j]));
                    obstheRules[i][j].addAntecedent(new FuzzyValue(obsphi, obsphiTerms[i]));
                    obstheRules[i][j].addConclusion(new FuzzyValue(obschangePhi, obsCurrentConclusions[i][j]));
                }
            }
        }
        catch (FuzzyException fe)
        {
            System.out.println(fe);
        }

    }

    // Set the truck to its initial set of Rule conclusions
    public void resetRules()
    {
        for (int i = 0; i < ROWS; i++)
            for (int j = 0; j < COLUMNS; j++)
            {
                CurrentConclusions[i][j] = DefaultConclusions[i][j];
            }
    }

    // Set the truck to its initial state before any computations
    public void reset()
    {
        Xt = X;
        Yt = Y;
        Phit = Phi;

        recompute = true;
        finished = false;
        truck_disabled = false;
        Iteration = 0;

        if (parent.viewArea.getGraphics() != null)
            parent.viewArea.paintComponent(parent.viewArea.getGraphics());
        parent.JLabelSimulationStatus.setText(" ");

        // reset background color of rule matrix buttons
        parent.resetConclusionButtonsBackground(OFF_COLOR);
        parent.resetConclusionButtonsBackground(OFF_COLOR);
    }

    public void setX(double x)
    {
        X = x;
    }

    public void setY(double y)
    {
        Y = y;
    }

    public void setTruckAngle(double a)
    {
        Phi = a;
    }

    public void setSimSpeed(int s)
    {
        SimSpeed = s;
    }

    public void setTruckSpeed(double s)
    {
        Speed = s;
    }

    public void setTracing(boolean b)
    {
        tracingWanted = b;
    }

    public boolean getTracing()
    {
        return tracingWanted;
    }

    public void setShowRuleFirings(boolean b)
    {
        showRuleFirings = b;
    }

    public boolean getShowRuleFirings()
    {
        return showRuleFirings;
    }

    public double getTruckAngle()
    {
        return Phi;
    }

    public int getSimSpeed()
    {
        return SimSpeed;
    }

    public double getTruckSpeed()
    {
        return Speed;
    }

    public int getRows()
    {
        return ROWS;
    }

    public int getColumns()
    {
        return COLUMNS;
    }

    public String getConclusionExpression(int i, int j)
    {
        return CurrentConclusions[i][j];
    }

    public String getobsConclusionExpression(int i, int j)
    {
        return obsCurrentConclusions[i][j];
    }

    public void setConclusionExpression(String s, int i, int j)
    {
        CurrentConclusions[i][j] = s;
    }


    // Core of the fuzzy truck algorithm
    public void run()
    {
        int m, i = 0, j = 0;
        double angleInRadians;
        double changePhiValue;
        boolean isObs = false;
        ResetConclusionButtonsThread rcbThread = new ResetConclusionButtonsThread();
        SetConclusionButtonColorThread scbcThread = new SetConclusionButtonColorThread(7);
        ViewAreaPaintComponentThread vapcThread = new ViewAreaPaintComponentThread();

        while (true)
        { // need sync. for button presses. when a msg.
            // comes in, we start the simulation.
            m = getMsg();

            if (!simulationFinished())
            {
                FuzzyValue globalResult = null;
                changePhiValue = 0.0;
                try
                {
                    // if this is the first time we run the alg.
                    // then get the rules from the matrix.
                    if (Iteration == 0)
                    {
                        Xt = X;
                        Yt = Y;
                        Phit = Phi;
                        // get the correct conclusions for each rule
                        for (i = 0; i < phiTerms.length; i++)
                            for (j = 0; j < xposTerms.length; j++)
                            {
                                String fzExpression = CurrentConclusions[i][j];
                                theRules[i][j].removeAllConclusions();
                                if (!fzExpression.equals(" "))
                                    theRules[i][j].addConclusion(new FuzzyValue(changePhi, fzExpression));
                            }
                    }
                    // reset the conclusion buttons backgrounds
                    SwingUtilities.invokeAndWait(rcbThread);
                    changePhiValue = getConclusion(7, Xt, Phit, xpos, phi, theRules);
                    // compute the results of the rule firing for the current xpos and phi values.

//                    // new values based on the alogorithm for finding the new
//                    // angle (see Kosko's book).
//                    // globalResult could be null if we take out some rules
//                    // that and no rules now match ...

                    //要在这里判断是否有障碍物
                    angleInRadians = (Phit + changePhiValue) * PIBY180;

                    double x = Speed * Math.cos(angleInRadians);
                    double y = Speed * Math.sin(angleInRadians);
                    double xb = 0;
                    double yb = 0;
                    int[][] coords = parent.viewArea.computeCoords(Xt + 4 * x, Yt - 4 * y, Phit);
                    boolean flag = false;
                    for (int k = 0; k < parent.viewArea.list.size(); k++)
                    {
                        int xa = parent.viewArea.list.get(k)[0];
                        int ya = parent.viewArea.list.get(k)[1];
                        int width = parent.viewArea.list.get(k)[2];
                        int height = parent.viewArea.list.get(k)[3];
                        int[][] obs = {{xa, ya}, {xa + width, ya}, {xa + width, ya + height}, {xa, ya + height}};
//                        System.out.println(Arrays.deepToString(obs));
                        if (isObs || SAT(coords, obs))
                        {
                            System.out.println("碰撞");
                            //TODO: 可以写判断算法了,还是有一点问题
                            changePhiValue = getConclusion(5, ((Xt + 2) - (xa / 4.0)) / (width / 4.0), Phit, obsxpos, obsphi, obstheRules);
                            System.out.println("改变角度" + changePhiValue);
                            angleInRadians = (Phit + changePhiValue) * PIBY180;
                            xb = Speed * Math.cos(angleInRadians);
                            yb = Speed * Math.sin(angleInRadians);
                            if(isObs)
                            {
                                isObs=false;
                            }
                            else
                            {
                                isObs = true;
                            }
                            flag = true;
                            break;
                        }
                    }
                    if (!flag)
                    {
                        Phit += changePhiValue;
                        Xt += x;
                        Yt -= y;
                        isObs=false;
                    }
                    else
                    {
                        System.out.println("避障专用路线");
                        Phit += changePhiValue;
                        //TODO:这里是有效的,不过系数是否2需要改进
                        if(isObs)
                        {
                            Xt -= 2 * x;
                            Yt += 2 * y;
                        }
                        Xt += xb;
                        Yt -= yb;
                    }

                }
                catch (FuzzyException fe)
                {
                    System.out.println(fe + "\n" + i + ", " + j);
                }
                catch (Throwable t)
                {
                    t.printStackTrace();
                    //Ensure the application exits with an error condition.
                    System.exit(1);
                }

                // compute the new coords for display
                parent.viewArea.computeTruckCoords();

                // test if the simulation is complete
                test(Xt, Yt, Phit);

                // update the applet
                Iteration++;
                tracingOn = tracingWanted;

                statusText = "Angle change = " + nf.format(changePhiValue) + ", Iteration = " + Iteration;
                try
                {
                    SwingUtilities.invokeLater(vapcThread);
                    SwingUtilities.invokeAndWait(sstThread);
                }
                catch (Throwable t)
                {
                    t.printStackTrace();
                    //Ensure the application exits with an error condition.
                    System.exit(1);
                }

                tracingOn = false;

                // if someone hit the pause button, we have to
                // stop running temporarily
                if (m == STEP) pause = true;
            }
        }
    }

    public double getConclusion(int length, double Xt, double Phit, FuzzyVariable xpos, FuzzyVariable phi, FuzzyRule[][] theRules)
    {
        FuzzyValueVector fvvInputs = new FuzzyValueVector(2);
        FuzzyValueVector result = null;
        FuzzyValue globalResult = null;
        double changePhiValue = 0;
        SetConclusionButtonColorThread scbcThread = new SetConclusionButtonColorThread(length);
        try
        {
            fvvInputs.addFuzzyValue(new FuzzyValue(xpos, new TriangleFuzzySet(Xt, Xt, Xt)));
            fvvInputs.addFuzzyValue(new FuzzyValue(phi, new TriangleFuzzySet(Phit, Phit, Phit)));
            for (int i = 0; i < length; i++)
            {
                for (int j = 0; j < xposTerms.length; j++)
                {
                    FuzzyValueVector concFvv = theRules[i][j].getConclusions();
                    if (concFvv != null &&
                            concFvv.size() > 0 &&
                            theRules[i][j].testRuleMatching(fvvInputs))
                    {
                        // execute rule with required input values
                        result = theRules[i][j].execute(fvvInputs);
                        FuzzyValue fv = result.fuzzyValueAt(0);
                        // set color of rule button background to indicate degree of matching
                        if (showRuleFirings)
                        {
                            double maxY = fv.getMaxY();
                            int colorDegree = (int) (255.0 * (1.0 - maxY));
                            if(length==7)
                            {
                                conclusionColor = matchColors[colorDegree];
                            }
                            else{
                                conclusionColor=obsmatchColors[colorDegree];
                            }
                            conclusionI = i;
                            conclusionJ = j;
                            SwingUtilities.invokeAndWait(scbcThread);
                        }

                        // add to global result for all rules
                        if (globalResult == null)
                            globalResult = fv;
                        else
                            globalResult = globalResult.fuzzyUnion(fv);
                    }
                }
            }
            if (globalResult != null)
            {
                changePhiValue = globalResult.momentDefuzzify();
            }
        }
        catch (FuzzyException e)
        {
            System.out.println(e + "\n");
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            System.exit(1);
        }
        return changePhiValue;
    }

    public double dot(int[] a, double[] b)
    {
        return a[0] * b[0] + a[1] * b[1];
    }

    public double[] normalize(int[] a)
    {
        double d = Math.sqrt(a[0] * a[0] + a[1] * a[1]);
        return new double[]{a[0] / d, a[1] / d};
    }

    public int[] perp(int[] a)
    {
        return new int[]{a[1], -a[0]};
    }

    public double[] project(int[][] a, int[] axis)
    {
        double[] tem = normalize(axis);
        double min = dot(a[0], tem);
        double max = min;
        for (int i = 0; i < a.length; i++)
        {
            double proj = dot(a[i], tem);
            if (proj < min)
                min = proj;
            if (proj > max)
                max = proj;
        }
        return new double[]{min, max};
    }

    boolean contains(double n, double[] range)
    {
        double a = range[0];
        double b = range[1];
        if (b < a)
        {
            a = b;
            b = range[0];
        }
        return (n >= a && n <= b);
    }

    boolean overlap(double[] a, double[] b)
    {
        if (contains(a[0], b))
            return true;
        if (contains(a[1], b))
            return true;
        if (contains(b[0], a))
            return true;
        if (contains(b[1], a))
            return true;
        return false;
    }

    public boolean SAT(int[][] a, int[][] b)
    {
        for (int i = 0; i < a.length; i++)
        {
            int[] axis;
            if (i != a.length - 1)
            {
                axis = new int[]{a[i + 1][0] - a[i][0], a[i + 1][1] - a[i][1]};
            }
            else
            {
                axis = new int[]{a[0][0] - a[a.length - 1][0], a[0][1] - a[a.length - 1][1]};
            }
            axis = perp(axis);
            double[] qa = project(a, axis);
            double[] qb = project(b, axis);
            if (!overlap(qa, qb))
                return false;
        }
        for (int i = 0; i < b.length; i++)
        {
            int[] axis;
            if (i != a.length - 1)
            {
                axis = new int[]{b[i + 1][0] - b[i][0], b[i + 1][1] - b[i][1]};
            }
            else
            {
                axis = new int[]{b[0][0] - b[b.length - 1][0], b[0][1] - b[b.length - 1][1]};
            }
            axis = perp(axis);
            double[] qa = project(a, axis);
            double[] qb = project(b, axis);
            if (!overlap(qa, qb))
                return false;
        }
        return true;
    }

    // test if the sim. is complete or if the truck left the
    // canvas.
    private void test(double x, double y, double angle)
    {
        if ((y <= 0.3) &&
                (Math.abs(50.0 - x) <= 1.0) &&
                (Math.abs(angle - 90.0) <= 5.0)
        )
        {
            finished = true;
            return;
        }
        if ((y < 0) || (x < 0) || (x > 100) || ((y > 100) &&
                (Math.abs(x - 50) > 1)))
            truck_disabled = true;
    }

    // print the information if the sim. is complete or
    // if the truck has left the canvas.
    public boolean simulationFinished()
    {
        try
        {
            if (finished)
            {
                statusText = "Simulation Complete - parking successful, Iterations = " + Iteration;
                SwingUtilities.invokeLater(sstThread);
                pause = true;
                return true;
            }
            if (truck_disabled)
            {
                statusText = "Simulation Complete - parking unsuccessful, Iterations = " + Iteration;
                SwingUtilities.invokeLater(sstThread);
                pause = true;
                return true;
            }
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            //Ensure the application exits with an error condition.
            System.exit(1);
        }

        return false;
    }

    // check for any button presses
    public synchronized int getMsg()
    {
        // disable tooltips while simulation running ... they appear to cause 
        // problems with painting .... sometimes???
        if (pause)
        {
            if (!(ToolTipManager.sharedInstance().isEnabled()))
                ToolTipManager.sharedInstance().setEnabled(true);
        }
        else
        {
            if (ToolTipManager.sharedInstance().isEnabled())
                ToolTipManager.sharedInstance().setEnabled(false);
        }

        while (pause)
        {
            try
            {
                wait(500);
            }
            catch (InterruptedException e)
            {
            }
        }

        try
        {
            if (SimSpeed > 1)
                sleep(10 * (SimSpeed - 1));
            else
                yield();
        }
        catch (InterruptedException e)
        {
        }
        return msg;
    }

    // external classes send messages to this class to
    // execute or stop iterating
    public synchronized void putMsg(int m)
    {
        msg = m;
        pause = (msg != RUN) && (msg != STEP);
//		notify();
    }


    class ResetConclusionButtonsThread implements Runnable
    {
        public void run()
        {
            // reset background color of rule matrix buttons
            parent.resetConclusionButtonsBackground(OFF_COLOR);
        }
    }

    class SetConclusionButtonColorThread implements Runnable
    {
        int type;

        public SetConclusionButtonColorThread(int type)
        {
            this.type = type;
        }

        public void run()
        {
            // set background color of a rule matrix conclusion button
            parent.setConclusionButtonBackground(conclusionI, conclusionJ, conclusionColor, type);
        }
    }

    class SetStatusTextThread implements Runnable
    {
        public void run()
        {
            // set text in status area
            parent.JLabelSimulationStatus.setText(statusText);
        }
    }

    class ViewAreaPaintComponentThread implements Runnable
    {
        public void run()
        {
            // paint the truck driving area
            parent.viewArea.paintComponent(parent.viewArea.getGraphics());
        }
    }
}
