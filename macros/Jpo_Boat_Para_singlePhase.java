
/**
 * DFBI simulation for planing boat hulls
 *
 * @author Andrew Gunderson
 *
 * 2017, v11.06
 */
import java.io.*;
import java.util.*;
import macroutils.*;
import star.common.*;
import org.apache.commons.math3.stat.descriptive.*;
import org.apache.poi.ss.usermodel.*;
import com.opencsv.CSVReader;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import star.base.neo.DoubleVector;
import star.flow.VelocityMagnitudeProfile;
import star.meshing.*;
import star.vis.*;
import star.vof.*;

public class Jpo_Boat_Para_SinglePhase extends StarMacro {

    /* RUN TITLE */
    String title = "310slx_hydro";

    /* RUN MATRIX */
    double[] sinks = {24.5, 25.5, 26.5}; // in
    double[] pitches = {-.2, .8, 1.8}; // deg
    double[] yaws = {0., 22.5, 45, 67.5, 90., 112.5, 135., 157.5, 180.};
    double[] speedsForward = {.25, .5, 1, 2, 3, 5, 10}; // fps
    double[] speedsAngle = {.25, .5, 1, 2}; // fps
    double[] speeds;

    double roll = 0.;
    int iterations = 500;
    int resx = 1200;
    int resy = 700;

    public void execute() {

        initMacro();
        for (double sink : sinks) {

            for (double pitch : pitches) {

                for (double yaw : yaws) {
                    pre(sink, pitch, yaw);
                    if (yaw == 0.) {
                        speeds = speedsForward;
                    } else {
                        speeds = speedsAngle;
                    }
                    mu.io.say.value("speeds", Arrays.toString(speeds), null, vo);
                    
                    for (double speed : speeds) {
                        ud.simTitle = title
                                + "_sink" + sink
                                + "_roll" + roll
                                + "_pitch" + pitch
                                + "_yaw" + yaw
                                + "_speed" + speed;
                        solve(speed);
                        try {
                            post(sink, pitch, yaw, speed);
                        } catch (Exception ex) {
                            mu.getSimulation().println(ex);
                        }
                    }
                }
            }
        }
    }

    void initMacro() {
        mu = new MacroUtils(getActiveSimulation());
        ud = mu.userDeclarations;
        ud.defUnitLength = ud.unit_in;
        ud.defColormap = mu.get.objects.colormap(
                StaticDeclarations.Colormaps.BLUE_RED);
      
    }

    void pre(double sink, double pitch, double yaw) {

        // set boat orientation
        tpo = (TransformPartsOperation) mu.getSimulation()
                .get(MeshOperationManager.class).getObject("Transform");
        rcRoll = (RotationControl) tpo.getTransforms().getObject("roll");
        rcRoll.getAngle().setValue(roll);
        rcPitch = (RotationControl) tpo.getTransforms().getObject("pitch");
        rcPitch.getAngle().setValue(pitch);
        rcYaw = (RotationControl) tpo.getTransforms().getObject("yaw");
        rcYaw.getAngle().setValue(yaw);
        tcSink = (TranslationControl) tpo.getTransforms().getObject("sink");
        tcSink.getTranslationVector().setCoordinate(ud.unit_in, ud.unit_in, ud.unit_in, new DoubleVector(new double[]{0., 0., sink}));

        // set c-sys orientation
        sinkCsys = (CartesianCoordinateSystem) ud.lab0
                .getLocalCoordinateSystemManager().getObject("sink");
        // sink
        sinkCsys.getOrigin().setCoordinate(ud.unit_in, ud.unit_in, ud.unit_in,
                new DoubleVector(new double[]{
            0.0, 0.0, sink}));
        // yaw
        yawCsys = (CartesianCoordinateSystem) sinkCsys
                .getLocalCoordinateSystemManager().getObject("yaw");
        yawCsys.setBasis0(new DoubleVector(new double[]{
            Math.cos(yaw * Math.PI / 180),
            Math.sin(yaw * Math.PI / 180),
            0.0
        }));
        // roll
        rollTrimCsys = (CartesianCoordinateSystem) yawCsys
                .getLocalCoordinateSystemManager().getObject("roll_trim");
        rollTrimCsys.setBasis1(new DoubleVector(new double[]{
            0.0,
            Math.cos(roll * Math.PI / 180),
            Math.sin(roll * Math.PI / 180)
        }));
        // trim
        rollTrimCsys.setBasis0(new DoubleVector(new double[]{
            Math.cos(pitch * Math.PI / 180),
            0.0,
            Math.sin(-pitch * Math.PI / 180)
        }));

        mu.clear.solution();
        mu.update.volumeMesh();
    }

    void solve(double speed) {
        // set inlet speed
        mu.get.boundaries.byREGEX("inlet", vo).getValues()
                .get(VelocityMagnitudeProfile.class).getMethod(
                ConstantScalarProfileMethod.class).getQuantity()
                .setValue(speed);

        mu.step(iterations);
        for (Displayer d : mu.get.scenes.allDisplayers(vo)) {
            d.setRepresentation(mu.get.mesh.fvr());
        }
        mu.saveSim();
    }

    void post(double sink, double pitch, double yaw, double speed) throws Exception {
        // export plots
        //ud.picPath = ud.simPath + "/" + ud.simTitle;
        //mu.io.write.plots();

        // export waterline 2d scene
        ud.scene = mu.get.scenes.byREGEX("waterline", vo);
        mu.io.write.picture(ud.scene, ud.simTitle, resx, resy, vo);

        // update excel with numerical results
        String ssTitle = ud.simPath + "/results.xls";
        if (!new File(ssTitle).exists()) {
            wb = new HSSFWorkbook();
            sheet = wb.createSheet("data");
            row = sheet.createRow(0);
            row.createCell(0).setCellValue("Sink");
            row.createCell(1).setCellValue("Pitch");
            row.createCell(2).setCellValue("Yaw");
            row.createCell(3).setCellValue("Speed");
            
            for (i = 4; i < reports.length; i++) {
                row.createCell(i + 1).setCellValue(reports[i]);
                out = new FileOutputStream(ssTitle);
                wb.write(out);
                out.close();
            }
        }
        // open existing wb
        wb = WorkbookFactory.create(new File(ssTitle));
        sheet = wb.getSheet("data");
        int currentRow = sheet.getLastRowNum() + 1;
        row = sheet.createRow(currentRow);
        // create row headers
        row.createCell(0).setCellValue(sink);
        row.createCell(1).setCellValue(pitch);
        row.createCell(2).setCellValue(yaw);
        row.createCell(3).setCellValue(speed);
 
        resultsCol = 4;
        for (String rep : reports) {
            ud.rep = mu.get.reports.byREGEX(rep, vo);
            row.createCell(resultsCol).setCellValue(
                    ud.rep.getReportMonitorValue());
            resultsCol++;
        }
        out = new FileOutputStream(ssTitle);
        wb.write(out);
        out.close();

        mu.clear.solutionHistory();
    }

    MacroUtils mu;
    UserDeclarations ud;
    boolean vo = true;

    Double yaw;
    List<String[]> data;
    Workbook wb;
    Sheet sheet;
    Row row;
    CSVReader reader;
    SummaryStatistics stats;
    FileOutputStream out;
    AutoSave as;
    int i;
    int resultsCol;
    double tStep;
    String[] reports = {"Fx", "Fy", "Fz", "Mx", "My", "Mz", "Lift", "Drag"};

    VofWaveModel vwm;
    FlatVofWave fvw;
    TransformPartsOperation tpo;
    RotationControl rcRoll;
    RotationControl rcPitch;
    RotationControl rcYaw;
    TranslationControl tcSink;
    CartesianCoordinateSystem sinkCsys;
    CartesianCoordinateSystem yawCsys;
    CartesianCoordinateSystem rollTrimCsys;

}
