package org.firstinspires.ftc.teamcode.opmodes;
// TODO make robot not hit partner/skybridge
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.roadrunner.control.PIDCoefficients;
import com.acmerobotics.roadrunner.control.PIDFController;
import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.acmerobotics.roadrunner.path.heading.ConstantInterpolator;
import com.acmerobotics.roadrunner.path.heading.LinearInterpolator;
import com.acmerobotics.roadrunner.path.heading.SplineInterpolator;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.internal.system.AppUtil;
import org.firstinspires.ftc.teamcode.NewStoneDetector;
import org.firstinspires.ftc.teamcode.drive.mecanum.VertexDrive;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import org.openftc.easyopencv.OpenCvInternalCamera;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
/*
 * Op mode for tuning follower PID coefficients (located in the drive base classes). The robot
 * drives in a DISTANCE-by-DISTANCE square indefinitely.
 */
@Config
@Autonomous(group = "drive", name = "Blue Two Block Auto")
public class BlueTwoBlockAuto extends LinearOpMode {
    private OpenCvInternalCamera phoneCam;
    private NewStoneDetector skyStoneDetector;

    ServoController robot = new ServoController();
    LiftController lift = null;

    enum State {
        INTAKE_OUT_AND_IN, RESET_SERVOS, STOP_INTAKE, START_INTAKE, REVERSE_INTAKE,
        DROP_GRIPPERS_HALFWAY, DROP_GRIPPERS_FULLY, LIFT_GRIPPERS, GO_TO_STACK_POSITION,
        GO_TO_LIFT_POSITION, DROP_BLOCK, GO_TO_BLOCK_2, GO_TO_FOUNDATION
    }
    @Override
    public void runOpMode() throws InterruptedException {
        // starts on blue side
        VertexDrive drive = new VertexDrive(hardwareMap);

        drive.setPoseEstimate(new Pose2d(0, 0, 0));
        robot.init(hardwareMap);
        lift = new LiftController(hardwareMap);

        // save last auto in file
        String fname = AppUtil.ROOT_FOLDER + "/lastAuto.txt";
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(fname));
            br.write("Blue");
            br.close();
        } catch (IOException exception) {

        }

        robot.pushServoReset();
        robot.gripRelease();
        robot.foundationReset();

        int blocksCollected = 0; // number of blocks collected so far

        // TODO detect stone position here
        // 65 (back), 96 (middle) 187 (front), 20 (front)

        // setup camera
        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        phoneCam = OpenCvCameraFactory.getInstance().createInternalCamera(OpenCvInternalCamera.CameraDirection.BACK, cameraMonitorViewId);
        phoneCam.openCameraDevice();
        skyStoneDetector = new NewStoneDetector(false);
        phoneCam.setPipeline(skyStoneDetector);
        phoneCam.startStreaming(320, 240, OpenCvCameraRotation.UPRIGHT);
        int stonePosition = 0;  // 0: front, 1: center, 2: back

        boolean exposureLocked = false;
        boolean xPressed = true;
        int exposureCompensation = 0;

        while(!isStarted()) {
            if (gamepad2.x) {
                if (!xPressed)
                    exposureLocked = !exposureLocked;
                xPressed = true;
            } else {
                xPressed = false;
            }

            if (!exposureLocked) {
                if (gamepad2.a) {
                    exposureCompensation = Math.max(exposureCompensation - 1, phoneCam.getMinSupportedExposureCompensation());
                } else if (gamepad2.b) {
                    exposureCompensation = Math.min(exposureCompensation + 1, phoneCam.getMaxSupportedExposureCompensation());
                }
            } else {
                telemetry.addLine("EXPOSURE LOCKED");
            }
            phoneCam.setExposureCompensation(exposureCompensation);
            phoneCam.setExposureLocked(exposureLocked);  // TODO check if this works

            telemetry.addData("EXPOSURE COMPENSATION", exposureCompensation);

            stonePosition = skyStoneDetector.position;
            if (stonePosition == 0) {
                telemetry.addData("position", "front");
            } else if (stonePosition == 1) {
                telemetry.addData("position", "center");
            } else if (stonePosition == 2) {
                telemetry.addData("position", "back");
            }
            telemetry.update();
        }


        phoneCam.pauseViewport();
        phoneCam.stopStreaming();

        ElapsedTime timer = new ElapsedTime();
        HashMap<State, Double> stateTimes = new HashMap<>();  // map of state -> start time

        Trajectory trajectory = null;

//        stonePosition = 0;
//        drive.setPoseEstimate(new Pose2d(84, -28, Math.toRadians(90)));


        if (stonePosition == 0) {
            telemetry.addLine("FRONT"); telemetry.update();
            trajectory = drive.trajectoryBuilder()
                    .addMarker(() -> {
                        stateTimes.put(State.RESET_SERVOS, null);
                        return null;
                    })
                    .lineTo(new Vector2d(-4, -24), new SplineInterpolator(0, Math.toRadians(-35)))
                    .lineTo(new Vector2d(-4, -29.5), new ConstantInterpolator(Math.toRadians(-35)))
                    .lineTo(new Vector2d(6, -32.5), new ConstantInterpolator(Math.toRadians(-35)))
                    .addMarker(new Vector2d(6, -32), () -> {
                        stateTimes.put(State.GO_TO_FOUNDATION, null);
                        return null;
                    })
                    .lineTo(new Vector2d(6, -31.5), new ConstantInterpolator(Math.toRadians(-35)))
//                    .lineTo(new Vector2d(8, -28), new ConstantInterpolator(Math.toRadians(-30)))
////                    .lineTo(new Vector2d(4, -24), new ConstantInterpolator(Math.toRadians(-30)))
//                    .lineTo(new Vector2d(23, -23), new LinearInterpolator(Math.toRadians(-30), Math.toRadians(-135)))
//                    .addMarker(new Vector2d(15, -23), () -> {
//                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
//                        stateTimes.put(State.DROP_GRIPPERS_HALFWAY, null);
//                        return null;3
//                    })
//                    .addMarker(new Vector2d(55, -23), () -> {
//                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
//                        return null;
//                    })
//                    .lineTo(new Vector2d(70, -23), new ConstantInterpolator(Math.toRadians(-180)))
//                    .addMarker(() -> {
//                        stateTimes.put(State.STOP_INTAKE, null);
//                        return null;
//                    })
//                    .lineTo(new Vector2d(87, -32), new SplineInterpolator(Math.toRadians(-180), Math.toRadians(-270)))
//                    .addMarker(new Vector2d(87, -34), () -> {
//                        stateTimes.put(State.DROP_GRIPPERS_FULLY, null);
//                        return null; // go to stack pos
//                    })
//                    .lineTo(new Vector2d(87, -36), new ConstantInterpolator(Math.toRadians(90)))
                    .build();
        } else if (stonePosition == 1) {
            telemetry.addLine("CENTER");
            telemetry.update();
            trajectory = drive.trajectoryBuilder()
                    .addMarker(() -> {
                        stateTimes.put(State.RESET_SERVOS, null);
                        return null;
                    })
                    .lineTo(new Vector2d(16.5, -27), new SplineInterpolator(0, Math.toRadians(-135)))
//                    .lineTo(new Vector2d(15, -27), new ConstantInterpolator(Math.toRadians(-135)))
                    .lineTo(new Vector2d(16.5, -29), new ConstantInterpolator(Math.toRadians(-135)))
                    .lineTo(new Vector2d(12.5, -34), new ConstantInterpolator(Math.toRadians(-135)))
                    .addMarker(new Vector2d(12.5, -33), () -> {
                        stateTimes.put(State.GO_TO_FOUNDATION, null);
                        return null;
                    })
                    .lineTo(new Vector2d(12.5, -32), new ConstantInterpolator(Math.toRadians(-135)))
//                    .lineTo(new Vector2d(26.5, -23), new SplineInterpolator(Math.toRadians(-135), Math.toRadians(-180)))
//                    .addMarker(() -> {
//                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
//                        return null;
//                    })
//                    .addMarker(new Vector2d(55, -23), () -> {
//                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
//                        return null;
//                    })
//                    .lineTo(new Vector2d(70, -23), new ConstantInterpolator(Math.toRadians(-180)))
//                    .addMarker(() -> {
//                        stateTimes.put(State.STOP_INTAKE, null);
//                        return null;
//                    })
//                    .lineTo(new Vector2d(87, -32), new SplineInterpolator(Math.toRadians(-180), Math.toRadians(-270)))
//                    .addMarker(new Vector2d(87, -34), () -> {
//                        stateTimes.put(State.DROP_GRIPPERS_FULLY, null);
//                        return null; // go to stack pos
//                    })
//                    .lineTo(new Vector2d(87, -36), new ConstantInterpolator(Math.toRadians(90)))
                    .build();

        } else {
            telemetry.addLine("BACK"); telemetry.update();
            trajectory = drive.trajectoryBuilder()
                    .addMarker(() -> {
                        stateTimes.put(State.RESET_SERVOS, null);
                        return null;
                    })
                    .lineTo(new Vector2d(9.5, -27), new SplineInterpolator(0, Math.toRadians(-135)))
//                    .lineTo(new Vector2d(15, -27), new ConstantInterpolator(Math.toRadians(-135)))
                    .lineTo(new Vector2d(9.5, -30), new ConstantInterpolator(Math.toRadians(-135)))
                    .lineTo(new Vector2d(4.5, -35), new ConstantInterpolator(Math.toRadians(-135)))
                    .addMarker(new Vector2d(4.5, -34), () -> {
                        stateTimes.put(State.GO_TO_FOUNDATION, null);
                        return null;
                    })
                    .lineTo(new Vector2d(4.5, -33), new ConstantInterpolator(Math.toRadians(-135)))
//                    .lineTo(new Vector2d(18.5, -23), new SplineInterpolator(Math.toRadians(-135), Math.toRadians(-180)))
//                    .addMarker(() -> {
//                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
//                        return null;
//                    })
//                    .addMarker(new Vector2d(55, -23), () -> {
//                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
//                        return null;
//                    })
//                    .lineTo(new Vector2d(70, -23), new ConstantInterpolator(Math.toRadians(-180)))
//                    .addMarker(() -> {
//                        stateTimes.put(State.STOP_INTAKE, null);
//                        return null;
//                    })
//                    .lineTo(new Vector2d(87, -32), new SplineInterpolator(Math.toRadians(-180), Math.toRadians(-270)))
//                    .addMarker(new Vector2d(87, -34), () -> {
//                        stateTimes.put(State.DROP_GRIPPERS_FULLY, null);
//                        return null; // go to stack pos
//                    })
//                    .lineTo(new Vector2d(87, -36), new ConstantInterpolator(Math.toRadians(90)))
                    .build();
        }

        drive.followTrajectory(trajectory);
//        robot.foundation_right.setPosition(0.2);
//        robot.foundation_left.setPosition(0.2);
//        sleep(500);
//        stateTimes.put(State.INTAKE_OUT_AND_IN, null);

        while (opModeIsActive()) {
            drive.update();

            double currentTime = timer.milliseconds();

            // set all start times if they don't exist
            for (State key : stateTimes.keySet()) {
                if (stateTimes.get(key) == null) {
                    stateTimes.put(key, currentTime);
                }
            }

            if (stateTimes.containsKey(State.GO_TO_FOUNDATION)) {
                Double startTime = stateTimes.get(State.GO_TO_FOUNDATION);
                double elapsedTime = 0;
                if (startTime != null) {
                    elapsedTime = currentTime - startTime;
                }

                if (elapsedTime < 400) {
                    // pause for 400ms while intaking block
                    drive.setMotorPowers(0,0,0,0);
                } else {
                    stateTimes.remove(State.GO_TO_FOUNDATION);
                    if (blocksCollected == 0) {
                        if (stonePosition == 0) {
                            // front
                            trajectory = drive.trajectoryBuilder()
                                    .lineTo(new Vector2d(8, -28), new ConstantInterpolator(Math.toRadians(-30)))
                                    .lineTo(new Vector2d(23, -23), new LinearInterpolator(Math.toRadians(-30), Math.toRadians(-135)))
                                    .addMarker(new Vector2d(15, -23), () -> {
                                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
                                        stateTimes.put(State.DROP_GRIPPERS_HALFWAY, null);
                                        return null;
                                    })
                                    .addMarker(new Vector2d(55, -23), () -> {
                                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
                                        return null;
                                    })
                                    .lineTo(new Vector2d(70, -23), new ConstantInterpolator(Math.toRadians(-180)))
                                    .addMarker(() -> {
                                        stateTimes.put(State.STOP_INTAKE, null);
                                        return null;
                                    })
                                    .lineTo(new Vector2d(85, -32), new SplineInterpolator(Math.toRadians(-180), Math.toRadians(-270)))
                                    .addMarker(new Vector2d(85, -38), () -> {
                                        stateTimes.put(State.DROP_GRIPPERS_FULLY, null);
                                        return null; // go to stack pos
                                    })
                                    .lineTo(new Vector2d(85, -40), new ConstantInterpolator(Math.toRadians(90)))
                                    .build();
                        } else if (stonePosition == 1 || stonePosition == 2) {
                            // center/back
                            trajectory = drive.trajectoryBuilder()
                                    .lineTo(new Vector2d(26.5, -23), new SplineInterpolator(Math.toRadians(-135), Math.toRadians(-180)))
                                    .addMarker(() -> {
                                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
                                        return null;
                                    })
                                    .addMarker(new Vector2d(55, -23), () -> {
                                        stateTimes.put(State.INTAKE_OUT_AND_IN, null);
                                        return null;
                                    })
                                    .lineTo(new Vector2d(70, -23), new ConstantInterpolator(Math.toRadians(-180)))
                                    .addMarker(() -> {
                                        stateTimes.put(State.STOP_INTAKE, null);
                                        return null;
                                    })
                                    .lineTo(new Vector2d(85, -32), new SplineInterpolator(Math.toRadians(-180), Math.toRadians(-270)))
                                    .addMarker(new Vector2d(85, -38), () -> {
                                        stateTimes.put(State.DROP_GRIPPERS_FULLY, null);
                                        return null; // go to stack pos
                                    })
                                    .lineTo(new Vector2d(85, -40), new ConstantInterpolator(Math.toRadians(90)))
                                    .build();
                        }
                    } else {
                        // for the second block
//                        if (stonePosition == 0) {
                        // front
                        trajectory = drive.trajectoryBuilder()
                                .lineTo(new Vector2d(3.5, -29), new ConstantInterpolator(Math.toRadians(210)))
                                .addMarker(0.5, () -> {
                                    stateTimes.put(State.INTAKE_OUT_AND_IN, null);
                                    return null;
                                })
                                .lineTo(new Vector2d(18.5, -23), new SplineInterpolator(Math.toRadians(210), Math.toRadians(180)))
                                // go to stack midway though
                                .addMarker(new Vector2d(60, -24), () -> {
                                    stateTimes.put(State.GO_TO_STACK_POSITION, null);
                                    return null;
                                })
                                // approach foundation
                                .addMarker(new Vector2d(76, -24), () -> {
                                    stateTimes.put(State.STOP_INTAKE, null);
//                                    stateTimes.put(State.GO_TO_LIFT_POSITION, null);
                                    return null;
                                })
                                .lineTo(new Vector2d(40, -24), new ConstantInterpolator(Math.toRadians(180)))
                                .lineTo(new Vector2d(80, -20), new ConstantInterpolator(Math.toRadians(180)))
                                .build();
//                        }
                    }

                    drive.followTrajectory(trajectory);
                }
            }

            if (stateTimes.containsKey(State.INTAKE_OUT_AND_IN)) {
                // intake out and in case stone gets stuck
                Double startTime = stateTimes.get(State.INTAKE_OUT_AND_IN);
                double elapsedTime = 0;
                if (startTime != null) {
                    elapsedTime = currentTime - startTime;
                }

                telemetry.addData("INTAKE_OUT_AND_IN", elapsedTime);

                if (elapsedTime < 500) {
                    robot.left_intake.setPower(-0.2);
                    robot.right_intake.setPower(-0.2);
                    robot.intake_wheel.setPower(-1);
                } else if (elapsedTime < 2000) {
                    robot.left_intake.setPower(0.6);
                    robot.right_intake.setPower(0.6);
                    robot.intake_wheel.setPower(1);
                } else {
                    robot.left_intake.setPower(0);
                    robot.right_intake.setPower(0);
                    robot.intake_wheel.setPower(0);
                    stateTimes.remove(State.INTAKE_OUT_AND_IN);
                }
            }

            // start/stop/reverse intake
            if (stateTimes.containsKey(State.START_INTAKE)) {
                robot.left_intake.setPower(0.6);
                robot.right_intake.setPower(0.6);
                robot.intake_wheel.setPower(1);
                telemetry.addData("START_INTAKE", 0);
                stateTimes.remove(State.START_INTAKE);
            } else if (stateTimes.containsKey(State.STOP_INTAKE)) {
                robot.left_intake.setPower(0);
                robot.right_intake.setPower(0);
                robot.intake_wheel.setPower(0);
                telemetry.addData("STOP_INTAKE", 0);
                stateTimes.remove(State.STOP_INTAKE);
            } else if (stateTimes.containsKey(State.REVERSE_INTAKE)) {
                robot.left_intake.setPower(-0.6);
                robot.right_intake.setPower(-0.6);
                robot.intake_wheel.setPower(-1);
                telemetry.addData("REVERSE_INTAKE", 0);
                stateTimes.remove(State.REVERSE_INTAKE);
            }

            // reset servos
            if (stateTimes.containsKey(State.RESET_SERVOS)) {
                // initialize servos
                Double startTime = stateTimes.get(State.RESET_SERVOS);
                double elapsedTime = 0;
                if (startTime != null) {
                    elapsedTime = currentTime - startTime;
                }

                telemetry.addData("RESET_SERVOS", elapsedTime);

                if (elapsedTime < 500) {
                    robot.v4bWait();
                    robot.pushServoReset();
                    robot.gripRelease();
                    robot.foundationUp();
                } else if (elapsedTime < 1000) {
                    robot.left_intake.setPower(0.8);
                    robot.right_intake.setPower(0.8);
                } else {
                    // start intake
                    robot.left_intake.setPower(0.6);
                    robot.right_intake.setPower(0.6);
                    robot.intake_wheel.setPower(1);
                    robot.pushServoUp();
                    stateTimes.remove(State.RESET_SERVOS);
                }
            }

            // drop grippers fully
            if (stateTimes.containsKey(State.DROP_GRIPPERS_FULLY)) {
                robot.foundationDown();
                drive.setMotorPowers(0,0,0,0);
                sleep(500); // pause while grippers fall
                stateTimes.remove(State.DROP_GRIPPERS_FULLY);

                Trajectory trajectory2 = drive.trajectoryBuilder()
//                        .addMarker(() -> {
//                            stateTimes.put(State.GO_TO_LIFT_POSITION, null);
//                            return null;
//                        })
                        .addMarker(() -> {
                            stateTimes.put(State.GO_TO_STACK_POSITION, null);
                            return null;
                        })
                        .addMarker(1.6, () -> {
                            stateTimes.put(State.GO_TO_LIFT_POSITION, null);
                            return null;
                        })
                        .lineTo(new Vector2d(65, -19), new LinearInterpolator(Math.toRadians(90), Math.toRadians(90)))
                        .addMarker(() -> {
                            stateTimes.put(State.LIFT_GRIPPERS, null);
                            return null;
                        })
                        .lineTo(new Vector2d(61, -19), new ConstantInterpolator(Math.toRadians(180)))
                        .addMarker(new Vector2d(74, -19), () -> {
                            stateTimes.put(State.DROP_BLOCK, null);
                            return null;
                        })
                        .lineTo(new Vector2d(76, -19), new ConstantInterpolator(Math.toRadians(180)))
                        .build();
                drive.followTrajectory(trajectory2);
            }

            if (stateTimes.containsKey(State.LIFT_GRIPPERS)) {
                robot.foundationUp();
                stateTimes.remove(State.LIFT_GRIPPERS);
            }

            if (stateTimes.containsKey(State.GO_TO_STACK_POSITION)) {
                // initialize servos
                Double startTime = stateTimes.get(State.GO_TO_STACK_POSITION);
                double elapsedTime = 0;
                if (startTime != null) {
                    elapsedTime = timer.milliseconds() - startTime;
                }

                if (elapsedTime < 300) {
                    robot.pushServoDown();
                } else if (elapsedTime < 600) {
                    robot.pushServoUp();
                    robot.gripRelease();
                } else if (elapsedTime < 900) {
                    robot.pushServoDown();
                } else if (elapsedTime < 1200) {
                    robot.v4bStack();
                } else if (elapsedTime < 1500) {
                    robot.grip();
                } else {
                    stateTimes.remove(State.GO_TO_STACK_POSITION);
                    if (blocksCollected > 0) {
                        stateTimes.put(State.GO_TO_LIFT_POSITION, null);
                    }
                }
            }

            if (stateTimes.containsKey(State.DROP_BLOCK)) {
                // initialize servos
                Double startTime = stateTimes.get(State.DROP_BLOCK);
                double elapsedTime = 0;
                if (startTime != null) {
                    elapsedTime = timer.milliseconds() - startTime;
                }

                if (elapsedTime < 300) {
                    robot.v4bDown();
                } else if (elapsedTime < 700) {
                    robot.gripRelease();
                } else if (elapsedTime < 800) {
                    if (blocksCollected == 2) {
                        Trajectory trajectory2 = drive.trajectoryBuilder()
                                .lineTo(new Vector2d(76, -28), new ConstantInterpolator(Math.toRadians(180)))
                                .build();
                        drive.followTrajectorySync(trajectory2);
                    }
                } else if (elapsedTime < 1200 || (blocksCollected == 2 && elapsedTime < 2200)) {
                    // reset v4b's to grab position
                    if (lift.mode == LiftController.Mode.STOPPED) {
                        lift.moveToPosition(lift.currentPosition + 2);
                    }
                } else if (elapsedTime < 1500 || (blocksCollected == 2 && elapsedTime < 2500)) {
                    robot.v4bStack();
                } else if (elapsedTime < 1800 || (blocksCollected == 2 && elapsedTime < 2800)) {
                    if (lift.mode == LiftController.Mode.STOPPED) {
                        lift.moveToPosition(0);
                    }
                } else if (elapsedTime < 2000 || (blocksCollected == 2 && elapsedTime < 3000)) {
                    // reset v4b's to wait position
                    robot.v4bWait();
                } else {
                    stateTimes.remove(State.DROP_BLOCK);
                    if (blocksCollected == 1) {
                        Trajectory trajectory2 = drive.trajectoryBuilder()
                                .lineTo(new Vector2d(76, -24), new ConstantInterpolator(Math.toRadians(180)))
                                .build();
                        drive.followTrajectorySync(trajectory2);
                        stateTimes.put(State.GO_TO_BLOCK_2, null);
                    } else {
                        // park
                        Trajectory trajectory3 = drive.trajectoryBuilder()
                                .lineTo(new Vector2d(40, -28), new ConstantInterpolator(Math.toRadians(180)))
                                .build();
                        drive.followTrajectorySync(trajectory3);
                    }

                }
            }

            if (stateTimes.containsKey(State.GO_TO_LIFT_POSITION)) {
                // initialize servos
                Double startTime = stateTimes.get(State.GO_TO_LIFT_POSITION);
                double elapsedTime = 0;
                if (startTime != null) {
                    elapsedTime = timer.milliseconds() - startTime;
                }

                if (elapsedTime < 300) {
                    // get pusher out of the way
//                    robot.grip();
                    robot.pushServoUp();
                } else if (elapsedTime < 500){
                    if (lift.mode == LiftController.Mode.STOPPED) {
                        if (blocksCollected == 0) {
                            lift.moveToPosition(3.3755);
                        } else {
                            lift.moveToPosition(7);
                        }
                    }
                } else if (lift.mode == LiftController.Mode.STOPPED) {
                    stateTimes.remove(State.GO_TO_LIFT_POSITION);
                    blocksCollected += 1;
                    if (blocksCollected >= 2) {
                        // drop block if more than one block collected.
                        // otherwise main thread does this.
                        stateTimes.put(State.DROP_BLOCK, null);
                    }
                }
            }

            if (stateTimes.containsKey(State.GO_TO_BLOCK_2)) {
                stateTimes.remove(State.GO_TO_BLOCK_2);
                if (stonePosition == 0) {
                    trajectory = drive.trajectoryBuilder()
                            .addMarker(() -> {
                                stateTimes.put(State.START_INTAKE, null);
                                return null;
                            })
                            .lineTo(new Vector2d(8.5, -32), new ConstantInterpolator(Math.toRadians(180)))
                            .lineTo(new Vector2d(4.5, -34), new LinearInterpolator(Math.toRadians(180), Math.toRadians(30)))
                            .lineTo(new Vector2d(4.5, -36), new ConstantInterpolator(Math.toRadians(210)))
                            .lineTo(new Vector2d(-2.5, -38), new ConstantInterpolator(Math.toRadians(210)))
                            .addMarker(new Vector2d(-2.5, -35), () -> {
                                stateTimes.put(State.GO_TO_FOUNDATION, null);
                                return null;
                            })
                            .lineTo(new Vector2d(-3.5, -34), new ConstantInterpolator(Math.toRadians(210)))
                            .build();
                } else if (stonePosition == 1) {
                    trajectory = drive.trajectoryBuilder()
                            .addMarker(() -> {
                                stateTimes.put(State.START_INTAKE, null);
                                return null;
                            })
                            .lineTo(new Vector2d(0.5, -32), new ConstantInterpolator(Math.toRadians(180)))
                            .lineTo(new Vector2d(-3.5, -34), new LinearInterpolator(Math.toRadians(180), Math.toRadians(30)))
                            .lineTo(new Vector2d(-3.5, -36), new ConstantInterpolator(Math.toRadians(210)))
                            .lineTo(new Vector2d(-10.5, -38), new ConstantInterpolator(Math.toRadians(210)))
                            .addMarker(new Vector2d(-10.5, -35), () -> {
                                stateTimes.put(State.GO_TO_FOUNDATION, null);
                                return null;
                            })
                            .lineTo(new Vector2d(-11.5, -34), new ConstantInterpolator(Math.toRadians(210)))
                            .build();
                } else {
                    trajectory = drive.trajectoryBuilder()
                            .addMarker(() -> {
                                stateTimes.put(State.START_INTAKE, null);
                                return null;
                            })
                            .lineTo(new Vector2d(-7.5, -32), new ConstantInterpolator(Math.toRadians(180)))
                            .lineTo(new Vector2d(-11.5, -34), new LinearInterpolator(Math.toRadians(180), Math.toRadians(30)))
                            .lineTo(new Vector2d(-11.5, -36), new ConstantInterpolator(Math.toRadians(210)))
                            .lineTo(new Vector2d(-18.5, -38), new ConstantInterpolator(Math.toRadians(210)))
                            .addMarker(new Vector2d(-18.5, -35), () -> {
                                stateTimes.put(State.GO_TO_FOUNDATION, null);
                                return null;
                            })
                            .lineTo(new Vector2d(-19.5, -34), new ConstantInterpolator(Math.toRadians(210)))
                            .build();
                }

                drive.followTrajectory(trajectory);
            }

            lift.update();
            telemetry.addData("stateTimes", stateTimes);
            telemetry.update();
        }
    }
}

