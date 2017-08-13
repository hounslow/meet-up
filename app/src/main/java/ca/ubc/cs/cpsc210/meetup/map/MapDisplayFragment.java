package ca.ubc.cs.cpsc210.meetup.map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.List;

import ca.ubc.cs.cpsc210.meetup.R;
import ca.ubc.cs.cpsc210.meetup.model.Building;
import ca.ubc.cs.cpsc210.meetup.model.Course;
import ca.ubc.cs.cpsc210.meetup.model.CourseFactory;
import ca.ubc.cs.cpsc210.meetup.model.Place;
import ca.ubc.cs.cpsc210.meetup.model.PlaceFactory;
import ca.ubc.cs.cpsc210.meetup.model.Section;
import ca.ubc.cs.cpsc210.meetup.model.Student;
import ca.ubc.cs.cpsc210.meetup.model.Schedule;
import ca.ubc.cs.cpsc210.meetup.model.StudentManager;
import ca.ubc.cs.cpsc210.meetup.util.LatLon;
import ca.ubc.cs.cpsc210.meetup.util.SchedulePlot;

import static ca.ubc.cs.cpsc210.meetup.util.LatLon.distanceBetweenTwoLatLon;

/**
 * Fragment holding the map in the UI.
 */
public class MapDisplayFragment extends Fragment {

    /**
     * Log tag for LogCat messages
     */
    private final static String LOG_TAG = "MapDisplayFragment";

    /**
     * Preference manager to access user preferences
     */
    private SharedPreferences sharedPreferences;

    /**
     * String to know whether we are dealing with MWF or TR schedule.
     * You will need to update this string based on the settings dialog at appropriate
     * points in time. See the project page for details on how to access
     * the value of a setting.
     */
    private String activeDay = "MWF";


    /**
     * A central location in campus that might be handy.
     */
    private final static GeoPoint UBC_MARTHA_PIPER_FOUNTAIN = new GeoPoint(49.264865,
            -123.252782);

    /**
     * Meetup Service URL
     * CPSC 210 Students: Complete the string.
     */
    private final String getStudentURL = "http://kramer.nss.cs.ubc.ca:8081/getStudent";

    /**
     * FourSquare URLs. You must complete the client_id and client_secret with values
     * you sign up for.
     */
    private static String FOUR_SQUARE_URL = "https://api.foursquare.com/v2/venues/explore";
    private static String FOUR_SQUARE_CLIENT_ID = "YTEXKDZWFWCG0APOI2BU3ZBIMZQZOPYLDLCVC0SURK5WGJYK";
    private static String FOUR_SQUARE_CLIENT_SECRET = "0GHGTTJYTN10PJ11UDWSTXD1A0L4SXUR0OF3WBIR0XGJ5TLS";


    /**
     * Overlays for displaying my schedules, buildings, etc.
     */
    private List<PathOverlay> scheduleOverlay;
    private ItemizedIconOverlay<OverlayItem> buildingOverlay;
    private OverlayItem selectedBuildingOnMap;

    /**
     * View that shows the map
     */
    private MapView mapView;

    /**
     * Access to domain model objects. Only store "me" in the studentManager for
     * the base project (i.e., unless you are doing bonus work).
     */
    private StudentManager studentManager;
    private Student randomStudent = null;
    private Student me = null;
    private static int ME_ID = 999999;

    /**
     * Map controller for zooming in/out, centering
     */
    private IMapController mapController;

    // ******************** Android methods for starting, resuming, ...

    // You should not need to touch this method
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d("onActivityCreated","============================================");
        setHasOptionsMenu(true);
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        scheduleOverlay = new ArrayList<PathOverlay>();

        // You need to setup the courses for the app to know about. Ideally
        // we would access a web service like the UBC student information system
        // but that is not currently possible
        initializeCourses();

        // Initialize the data for the "me" schedule. Note that this will be
        // hard-coded for now
        initializeMySchedule();

        // You are going to need an overlay to draw buildings and locations on the map
        buildingOverlay = createBuildingOverlay();
    }

    // You should not need to touch this method
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
    }

    // You should not need to touch this method
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (mapView == null) {
            mapView = new MapView(getActivity(), null);

            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);

            mapController = mapView.getController();
            mapController.setZoom(mapView.getMaxZoomLevel() - 2);
            mapController.setCenter(UBC_MARTHA_PIPER_FOUNTAIN);
        }

        return mapView;
    }

    // You should not need to touch this method
    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView");
        ((ViewGroup) mapView.getParent()).removeView(mapView);
        super.onDestroyView();
    }

    // You should not need to touch this method
    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    // You should not need to touch this method
    @Override
    public void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
    }

    // You should not need to touch this method
    @Override
    public void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
    }

    /**
     * Save map's zoom level and centre. You should not need to
     * touch this method
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            outState.putInt("zoomLevel", mapView.getZoomLevel());
            IGeoPoint cntr = mapView.getMapCenter();
            outState.putInt("latE6", cntr.getLatitudeE6());
            outState.putInt("lonE6", cntr.getLongitudeE6());
            Log.i("MapSave", "Zoom: " + mapView.getZoomLevel());
        }
    }

    // ****************** App Functionality

    /**
     * Show my schedule on the map. Every time "me"'s schedule shows, the map
     * should be cleared of all existing schedules, buildings, meetup locations, etc.
     */
    public void showMySchedule() {
        initializeMySchedule();
        String day = sharedPreferences.getString("dayOfWeek", "MWF");
        SortedSet<Section> sectionSet = me.getSchedule().getSections(day);
        SchedulePlot mySchedulePlot = new SchedulePlot(
                sectionSet,
                "MATT HOUNSLOW",
                "#0000FF",
                R.drawable.ic_action_place);
        new GetRoutingForSchedule().execute(mySchedulePlot);

        // CPSC 210 Students: You must complete the implementation of this method.
        // The very last part of the method should call the asynchronous
        // task (which you will also write the code for) to plot the route
        // for "me"'s schedule for the day of the week set in the Settings

        // Asynchronous tasks are a bit onerous to deal with. In order to provide
        // all information needed in one object to plot "me"'s route, we
        // create a SchedulePlot object and pass it to the asynchronous task.
        // See the project page for more details.


        // Get a routing between these points. This line of code creates and calls
        // an asynchronous task to do the calls to MapQuest to determine a route
        // and plots the route.
        // Assumes mySchedulePlot is a create and initialized SchedulePlot object

        // UNCOMMENT NEXT LINE ONCE YOU HAVE INSTANTIATED mySchedulePlot
      //  new GetRoutingForSchedule().execute(mySchedulePlot);
    }

    /**
     * Retrieve a random student's schedule from the Meetup web service and
     * plot a route for the schedule on the map. The plot should be for
     * the given day of the week as determined when "me"'s schedule
     * was plotted.
     */
    public void showRandomStudentsSchedule() {
        // To get a random student's schedule, we have to call the MeetUp web service.
        // Calling this web service requires a network access to we have to
        // do this in an asynchronous task. See below in this class for where
        // you need to implement methods for performing the network access
        // and plotting.
        new GetRandomSchedule().execute();
    }

    /**
     * Clear all schedules on the map
     */
    public void clearSchedules() {
        randomStudent = null;
        OverlayManager om = mapView.getOverlayManager();
        om.clear();
        scheduleOverlay.clear();
        buildingOverlay.removeAllItems();
        om.addAll(scheduleOverlay);
        om.add(buildingOverlay);
        mapView.invalidate();
    }

    /**
     * Find all possible locations at which "me" and random student could meet
     * up for the set day of the week and the set time to meet and the set
     * distance either "me" or random is willing to travel to meet.
     * A meetup is only possible if both "me" and random are free at the
     * time specified in the settings and each of us must have at least an hour
     * (>= 60 minutes) free. You should display dialog boxes if there are
     * conditions under which no meetup can happen (e.g., me or random is
     * in class at the specified time)
     */
    public void findMeetupPlace() {
        PlaceFactory placeFactory = PlaceFactory.getInstance();
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        String timeOfDay = sharedPreferences.getString("timeOfDay", "12");

        List<Section> meSections = new ArrayList<>(studentManager.get(ME_ID).getSchedule().getSections(dayOfWeek));
        if (this.randomStudent == null) {
            AlertDialog aDialog = createSimpleDialog("No random student has been fetched!");
            aDialog.show();
        } else {
            List<Section> randomSections = new ArrayList<>(randomStudent.getSchedule().getSections(dayOfWeek));

            Schedule meSchedule = studentManager.get(ME_ID).getSchedule();
            Schedule randomSchedule = randomStudent.getSchedule();

            Set<String> meBreakTimes = meSchedule.getStartTimesOfBreaks(dayOfWeek);
            Set<String> randomBreakTimes = randomSchedule.getStartTimesOfBreaks(dayOfWeek);


            if (meBreakTimes.size() == 0 || randomBreakTimes.size() == 0) {
                if (meBreakTimes.size() == 0) {
                    AlertDialog aDialog = createSimpleDialog(studentManager.get(ME_ID).getFirstName() + " has no breaks today");
                    aDialog.show();
                } else {
                    AlertDialog alertDialog = createSimpleDialog(randomStudent.getFirstName() + " has no breaks today");
                    alertDialog.show();
                }
            } else if (!meSchedule.hasBreakTimeOfDay(timeOfDay, dayOfWeek) || !randomSchedule.hasBreakTimeOfDay(timeOfDay, dayOfWeek)) {
                AlertDialog aDialog = createSimpleDialog("You have no overlapping breaks today");
                aDialog.show();

            } else {
                Building myBuilding = null;
                Building randomBuilding = null;
                try {
                    myBuilding = meSchedule.whereAmI(dayOfWeek, timeOfDay);

                } catch (NullPointerException e) {

                }
                try {
                    randomBuilding = randomSchedule.whereAmI(dayOfWeek, timeOfDay);

                } catch (NullPointerException e) {

                } finally {
                    if (myBuilding == null && randomBuilding == null) {
                        AlertDialog alertDialog = createSimpleDialog("You have been in no buildings today");
                        alertDialog.show();

                    } else if (randomBuilding == null) {
                        LatLon myLocation = myBuilding.getLatLon();
                        List<Place> myPlaces = new ArrayList<>(placeFactory.findPlacesWithinDistance(myLocation, sharedPreferences.getInt("placeDistanceValues", 1000)));
                        for (int i = 0; i < myPlaces.size(); i++) {
                            Place place = myPlaces.get(i);
                            String name = myPlaces.get(i).getName();
                            plotAPlace(place, name, name + " has food!", R.drawable.ic_action_cancel);

                        }

                    } else if (myBuilding == null) {
                        LatLon randomLocation = randomBuilding.getLatLon();
                        List<Place> randomPlaces = new ArrayList<>(placeFactory.findPlacesWithinDistance(randomLocation, sharedPreferences.getInt("placeDistanceValues", 1000)));
                        for (int i = 0; i < randomPlaces.size(); i++) {
                            Place place = randomPlaces.get(i);
                            String name = randomPlaces.get(i).getName();
                            plotAPlace(place, name, name + " has food!", R.drawable.ic_action_cancel);
                        }
                    } else {
                        LatLon myLocation = myBuilding.getLatLon();
                        LatLon randomLocation = randomBuilding.getLatLon();
                        double distance = distanceBetweenTwoLatLon(myLocation, randomLocation);

                        List<Place> myPlaces = new ArrayList<>(placeFactory.findPlacesWithinDistance(myLocation, sharedPreferences.getInt("placeDistanceValues", 1000)));
                        List<Place> randomPlaces = new ArrayList<>(placeFactory.findPlacesWithinDistance(randomLocation, sharedPreferences.getInt("placeDistanceValues", 1000)));
                        List<Place> subset = new ArrayList<>();
                        if(myPlaces.size() > randomPlaces.size()){
                            for (int n = 0; n < myPlaces.size(); n++) {
                                Place place2 = myPlaces.get(n);
                                if(randomPlaces.contains(place2)){
                                   subset.add(place2);
                                }

                            }
                        } else {
                            for (int n = 0; n < randomPlaces.size(); n++) {
                                Place place2 = randomPlaces.get(n);
                                if(myPlaces.contains(place2)){
                                    subset.add(place2);
                                }

                            }
                        }

                        for (int i = 0; i < subset.size(); i++) {
                            Place place = subset.get(i);
                            String name = subset.get(i).getName();
                            plotAPlace(place, name, name + " has food!", R.drawable.ic_action_cancel);
                        }

                    }
                    }
                }
            }
        }



    /**
     * Initialize the PlaceFactory with information from FourSquare
     */
    public void initializePlaces() {
        // CPSC 210 Students: You should not need to touch this method, but
        // you will have to implement GetPlaces below.
        new GetPlaces().execute();
    }




    /**
     * Plot all buildings referred to in the given information about plotting
     * a schedule.
     * @param schedulePlot All information about the schedule and route to plot.
     */
    private void plotBuildings(SchedulePlot schedulePlot) {
        // CPSC 210 Students: Complete this method by plotting each building in the
        // schedulePlot with an appropriate message displayed
        // CPSC 210 Students: You will need to ensure the buildingOverlay is in
        // the overlayManager. The following code achieves this. You should not likely
        // need to touch it
        ArrayList<Section> sections = new ArrayList<>(schedulePlot.getSections());
        OverlayManager om = mapView.getOverlayManager();

        for (int p = 0; p < sections.size(); p++) {
            Building building = sections.get(p).getBuilding();
            String title = sections.get(p).getBuilding().getName();
            String sectionName = sections.get(p).getName();
            String courseName = sections.get(p).getCourse().toString();
            String studentName = schedulePlot.getName();

           plotABuilding(building, title,  studentName + " has " + courseName + " " + sectionName, R.drawable.ic_action_place);
           om.add(buildingOverlay);
        }
    }
    /**
     * Plot a place onto the map
     * @param place The place to put on the map
     * @param title The title to put in the dialog box when the place is tapped on the map
     * @param msg The message to display when the place is tapped
     * @param drawableToUse The icon to use. Can be R.drawable.ic_action_place (or any icon in the res/drawable directory)
     */
    private void plotAPlace(Place place, String title, String msg, int drawableToUse) {
        // CPSC 210 Students: You should not need to touch this method
        OverlayItem placeItem = new OverlayItem(title, msg,
                new GeoPoint(place.getLatLon().getLatitude(), place.getLatLon().getLongitude()));

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        placeItem.setMarker(icon);
        buildingOverlay.addItem(placeItem);
    }

    /**
     * Plot a building onto the map
     * @param building The building to put on the map
     * @param title The title to put in the dialog box when the building is tapped on the map
     * @param msg The message to display when the building is tapped
     * @param drawableToUse The icon to use. Can be R.drawable.ic_action_place (or any icon in the res/drawable directory)
     */
    private void plotABuilding(Building building, String title, String msg, int drawableToUse) {
        // CPSC 210 Students: You should not need to touch this method
        OverlayItem buildingItem = new OverlayItem(title, msg,
                new GeoPoint(building.getLatLon().getLatitude(), building.getLatLon().getLongitude()));

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        buildingItem.setMarker(icon);
        buildingOverlay.addItem(buildingItem);
    }


    /**
     * Initialize your schedule by coding it directly in. This is the schedule
     * that will appear on the map when you select "Show My Schedule".
     */
    private void initializeMySchedule() {
        Log.d("initializeMySchedule","============================================");
        studentManager = new StudentManager();
        studentManager.addStudent("HOUNSLOW", "MATT", ME_ID);

        studentManager.addSectionToSchedule(ME_ID, "CPSC", 210, "201");
        studentManager.addSectionToSchedule(ME_ID, "ENGL", 222, "007");
        studentManager.addSectionToSchedule(ME_ID, "FREN", 102, "202");
        studentManager.addSectionToSchedule(ME_ID, "MATH", 200, "201");
        studentManager.addSectionToSchedule(ME_ID, "MATH", 221, "202");
        studentManager.addSectionToSchedule(ME_ID, "PHYS", 203, "201");
        me = studentManager.get(ME_ID);
//
        // CPSC 210 Students; Implement this method

    }

    /**
     * Helper to create simple alert dialog to display message
     *
     * @param msg message to display in alert dialog
     * @return the alert dialog
     */
    private AlertDialog createSimpleDialog(String msg) {
        // CPSC 210 Students; You should not need to modify this method
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
        dialogBldr.setMessage(msg);
        dialogBldr.setNeutralButton(R.string.ok, null);

        return dialogBldr.create();
    }

    /**
     * Create the overlay used for buildings. CPSC 210 students, you should not need to
     * touch this method.
     * @return An overlay
     */
    private ItemizedIconOverlay<OverlayItem> createBuildingOverlay() {
        ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

        ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gestureListener =
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

            /**
             * Display building description in dialog box when user taps stop.
             *
             * @param index
             *            index of item tapped
             * @param oi
             *            the OverlayItem that was tapped
             * @return true to indicate that tap event has been handled
             */
            @Override
            public boolean onItemSingleTapUp(int index, OverlayItem oi) {

                new AlertDialog.Builder(getActivity())
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                if (selectedBuildingOnMap != null) {
                                    mapView.invalidate();
                                }
                            }
                        }).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
                        .show();

                selectedBuildingOnMap = oi;
                mapView.invalidate();
                return true;
            }

            @Override
            public boolean onItemLongPress(int index, OverlayItem oi) {
                // do nothing
                return false;
            }
        };

        return new ItemizedIconOverlay<OverlayItem>(
                new ArrayList<OverlayItem>(), getResources().getDrawable(
                R.drawable.ic_action_place), gestureListener, rp);
    }


    /**
     * Create overlay with a specific color
     * @param colour A string with a hex colour value
     */
    private PathOverlay createPathOverlay(String colour) {
        Log.d("createPathOverlay","============================================");
        // CPSC 210 Students, you should not need to touch this method
        PathOverlay po = new PathOverlay(Color.parseColor(colour),
                getActivity());
        Paint pathPaint = new Paint();
        pathPaint.setColor(Color.parseColor(colour));
        pathPaint.setStrokeWidth(4.0f);
        pathPaint.setStyle(Paint.Style.STROKE);
        po.setPaint(pathPaint);
        return po;
    }

   // *********************** Asynchronous tasks

    /**
     * This asynchronous task is responsible for contacting the Meetup web service
     * for the schedule of a random student. The task must plot the retrieved
     * student's route for the schedule on the map in a different colour than the "me" schedule
     * or must display a dialog box that a schedule was not retrieved.
     */
    private class GetRandomSchedule extends AsyncTask<Void, Void, SchedulePlot> {

        // Some overview explanation of asynchronous tasks is on the project web page.

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(Void... params) {
            // CPSC 210 Students: You must complete this method. It needs to
            // contact the Meetup web service to get a random student's schedule.
            // If it is successful in retrieving a student and their schedule,
            // it needs to remember the student in the randomStudent field
            // and it needs to create and return a schedulePlot object with
            // all relevant information for being ready to retrieve the route
            // and plot the route for the schedule. If no random student is
            // retrieved, return null.
            //
            // Note, leave all determination of routing and plotting until
            // the onPostExecute method below.
            SchedulePlot schedulePlot = null;
              try {
                  String api = getStudentURL;
                  String jsonLink = makeRoutingCall(api);
                  JSONTokener jt = new JSONTokener(jsonLink);

                  JSONObject jo = new JSONObject(jt);
                  String firstName = jo.getString("FirstName");
                  String lastName = jo.getString("LastName");
                  String name = firstName + " " + lastName;
                  int id = jo.getInt("Id");
                  JSONArray sections = jo.getJSONArray("Sections");
                  studentManager.addStudent(lastName, firstName, id);
                  randomStudent = studentManager.get(id);


                  int i = 0;
                  while (i < sections.length()) {
                      JSONObject section = sections.getJSONObject(i);
                      String courseName = section.getString("CourseName");
                      int courseNumber = section.getInt("CourseNumber");
                      String sectionName = section.getString("SectionName");
                      studentManager.addSectionToSchedule(id, courseName, courseNumber, sectionName);
                      i++;
                  }
                  String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
                  SortedSet<Section> sectionSet = studentManager.get(id).getSchedule().getSections(dayOfWeek);


                  schedulePlot = new SchedulePlot(
                          sectionSet,
                          name,
                          "#FF0000",
                          R.drawable.ic_action_place);

              } catch (JSONException e) {
                  e.printStackTrace();
              } catch (MalformedURLException e) {
                  e.printStackTrace();
              } catch (IOException e) {
                  e.printStackTrace();
                  Log.d(LOG_TAG, e.getMessage());
              } finally {

              } return schedulePlot;
        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {
            // CPSC 210 students: When this method is called, it will be passed
            // whatever schedulePlot object you created (if any) in doBackground
            // above. Use it to plot the route.
            new GetRoutingForSchedule().execute(schedulePlot);
        }

        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();

            return returnString;
        }
    }

    /**
     * This asynchronous task is responsible for contacting the MapQuest web service
     * to retrieve a route between the buildings on the schedule and for plotting any
     * determined route on the map.
     */
    private class GetRoutingForSchedule extends AsyncTask<SchedulePlot, Void, SchedulePlot> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(SchedulePlot... params) {
            // The params[0] element contains the schedulePlot object
            SchedulePlot scheduleToPlot = params[0];
            ArrayList<Section> sections = new ArrayList<>(scheduleToPlot.getSections());
            Log.d(LOG_TAG, sections.toString());
            List<GeoPoint> geoPointsList = new ArrayList<>();

            for (int i = 0; i < sections.size() - 1; i++) {

                Section sectionOne = sections.get(i);
                Section sectionTwo = sections.get(i+1);
                try {
                    double latFrom = sectionOne.getBuilding().getLatLon().getLatitude();
                    double lonFrom = sectionOne.getBuilding().getLatLon().getLongitude();
                    double latTo = sectionTwo.getBuilding().getLatLon().getLatitude();
                    double lonTo = sectionTwo.getBuilding().getLatLon().getLongitude();
                    String sLat1 = Double.toString(latFrom);
                    String sLon1 = Double.toString(lonFrom);
                    String sLat2 = Double.toString(latTo);
                    String sLon2 = Double.toString(lonTo);

                    String api = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82l08nh%2C8n%3Do5-94z554&callback=renderAdvancedNarrative&outFormat=json&routeType=pedestrian&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=m&from=" + sLat1 + "," + sLon1 + "&to=" + sLat2 + "," + sLon2 + "&drivingStyle=2&highwayEfficiency=21.0";
                    String jsonLink = makeRoutingCall(api);
                    jsonLink = jsonLink.substring("renderAdvancedNarrative(".length(), jsonLink.length() - 2);


                    JSONTokener jt = new JSONTokener(jsonLink);
                    JSONObject jo = new JSONObject(jt);
                    JSONObject route = jo.getJSONObject("route");
                    JSONObject shape = route.getJSONObject("shape");
                    JSONArray shapePoints = shape.getJSONArray("shapePoints");
                    int n = 0;
                    while (n < shapePoints.length()) {
                        double lat = shapePoints.getDouble(n);
                        n++;
                        double lon = shapePoints.getDouble(n);
                        n++;
                        GeoPoint newGeoPoint = new GeoPoint(lat, lon);


                        geoPointsList.add(newGeoPoint);

                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.d(LOG_TAG, e.getMessage());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    Log.d(LOG_TAG, e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(LOG_TAG, e.getMessage());
                } finally {
                    System.out.println("new GeoPoint added");
                }

            }
            Log.d(LOG_TAG, "==========================");
            scheduleToPlot.setRoute(geoPointsList);
            return scheduleToPlot;
        }




        /**
         * An example helper method to call a web service
         */
        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();

            return returnString;
        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {
            List<GeoPoint> geoPointPlotList = schedulePlot.getRoute();
            PathOverlay po = createPathOverlay(schedulePlot.getColourOfLine());
            OverlayManager om = mapView.getOverlayManager();

            for (int i = 0; i < geoPointPlotList.size(); i++) {
                GeoPoint point1 = geoPointPlotList.get(i);
                GeoPoint point2 = geoPointPlotList.get(i++);
                po.addPoint(point1);
                po.addPoint(point2);
                scheduleOverlay.add(po);
            }
            om.addAll(scheduleOverlay);
            mapView.invalidate(); // cause map to redraw
            plotBuildings(schedulePlot);
        }

    }

    /**
     * This asynchronous task is responsible for contacting the FourSquare web service
     * to retrieve all places around UBC that have to do with food. It should load
     * any determined places into PlaceFactory and then display a dialog box of how it did
     */
    private class GetPlaces extends AsyncTask<Void, Void, String> {

        protected String doInBackground(Void... params) {
            // CPSC 210 Students: Complete this method to retrieve a string
            // of JSON from FourSquare. Return the string from this method
            String jSONOfPlaces = null;
            try {
                String placesApi = "https://api.foursquare.com/v2/venues/explore?ll=49.264865%2C+-123.252782&ion%3Dfood&client_id="+FOUR_SQUARE_CLIENT_ID+"&client_secret="+FOUR_SQUARE_CLIENT_SECRET+"&v=20150402";
                jSONOfPlaces = makeRoutingCall(placesApi);
            } catch (IOException e) {
                e.printStackTrace();
            } return jSONOfPlaces;
        }

        protected void onPostExecute(String jSONOfPlaces) {
            // CPSC 210 Students: Given JSON from FourQuest, parse it and load
            // PlaceFactory
            PlaceFactory placeFactory = PlaceFactory.getInstance();
            List<Place> placesList = new ArrayList<>();
            int n = placesList.size();
                try {
                    JSONTokener jt = new JSONTokener(jSONOfPlaces);
                    JSONObject jo = new JSONObject(jt);
                    JSONObject response = jo.getJSONObject("response");
                    JSONArray groups = response.getJSONArray("groups");
                    JSONObject jo3 = groups.getJSONObject(0);
                    JSONArray items = jo3.getJSONArray("items");

                    int i = 0;
                    while (i < items.length()) {
                        JSONObject jo4 = items.getJSONObject(i);
                        JSONObject venue = jo4.getJSONObject("venue");
                        JSONObject location = venue.getJSONObject("location");
                        String placeName = venue.getString("name");
                        double lat = location.getDouble("lat");
                        double lon = location.getDouble("lng");
                        LatLon latLon = new LatLon(lat, lon);

                        Place place = new Place(placeName, latLon);
                        placesList.add(place);
                        placeFactory.add(place);
                        i++;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                } finally {
                } AlertDialog aDialog = createSimpleDialog(String.valueOf(placesList.size()));
                    aDialog.show();
            }
        }

        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();

            return returnString;
        }


    /**
     * Initialize the CourseFactory with some courses.
     */
    private void initializeCourses() {
        // CPSC 210 Students: You can change this data if you desire.
        CourseFactory courseFactory = CourseFactory.getInstance();

        Building dmpBuilding = new Building("DMP", new LatLon(49.261474, -123.248060));

        Course cpsc210 = courseFactory.getCourse("CPSC", 210);
        Section aSection = new Section("202", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("201", "MWF", "16:00", "16:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("BCS", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);

        Course engl222 = courseFactory.getCourse("ENGL", 222);
        aSection = new Section("007", "MWF", "14:00", "14:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        engl222.addSection(aSection);
        aSection.setCourse(engl222);

        Course scie220 = courseFactory.getCourse("SCIE", 220);
        aSection = new Section("200", "MWF", "15:00", "15:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie220.addSection(aSection);
        aSection.setCourse(scie220);

        Course math200 = courseFactory.getCourse("MATH", 200);
        aSection = new Section("201", "MWF", "09:00", "09:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        math200.addSection(aSection);
        aSection.setCourse(math200);

        Course fren102 = courseFactory.getCourse("FREN", 102);
        aSection = new Section("202", "MWF", "11:00", "11:50", new Building("Barber", new LatLon(49.267442,-123.252471)));
        fren102.addSection(aSection);
        aSection.setCourse(fren102);

        Course japn103 = courseFactory.getCourse("JAPN", 103);
        aSection = new Section("002", "MWF", "10:00", "11:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        japn103.addSection(aSection);
        aSection.setCourse(japn103);

        Course scie113 = courseFactory.getCourse("SCIE", 113);
        aSection = new Section("213", "MWF", "13:00", "13:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie113.addSection(aSection);
        aSection.setCourse(scie113);

        Course micb308 = courseFactory.getCourse("MICB", 308);
        aSection = new Section("201", "MWF", "12:00", "12:50", new Building("Woodward", new LatLon(49.264704,-123.247536)));
        micb308.addSection(aSection);
        aSection.setCourse(micb308);

        Course math221 = courseFactory.getCourse("MATH", 221);
        aSection = new Section("202", "TR", "11:00", "12:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        math221.addSection(aSection);
        aSection.setCourse(math221);

        Course phys203 = courseFactory.getCourse("PHYS", 203);
        aSection = new Section("201", "TR", "09:30", "10:50", new Building("Hennings", new LatLon(49.266400,-123.252047)));
        phys203.addSection(aSection);
        aSection.setCourse(phys203);

        Course crwr209 = courseFactory.getCourse("CRWR", 209);
        aSection = new Section("002", "TR", "12:30", "13:50", new Building("Geography", new LatLon(49.266039,-123.256129)));
        crwr209.addSection(aSection);
        aSection.setCourse(crwr209);

        Course fnh330 = courseFactory.getCourse("FNH", 330);
        aSection = new Section("002", "TR", "15:00", "16:20", new Building("MacMillian", new LatLon(49.261167,-123.251157)));
        fnh330.addSection(aSection);
        aSection.setCourse(fnh330);

        Course cpsc499 = courseFactory.getCourse("CPSC", 430);
        aSection = new Section("201", "TR", "16:20", "17:50", new Building("Liu", new LatLon(49.267632,-123.259334)));
        cpsc499.addSection(aSection);
        aSection.setCourse(cpsc499);

        Course chem250 = courseFactory.getCourse("CHEM", 250);
        aSection = new Section("203", "TR", "10:00", "11:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        chem250.addSection(aSection);
        aSection.setCourse(chem250);

        Course eosc222 = courseFactory.getCourse("EOSC", 222);
        aSection = new Section("200", "TR", "11:00", "12:20", new Building("ESB", new LatLon(49.262866, -123.25323)));
        eosc222.addSection(aSection);
        aSection.setCourse(eosc222);

        Course biol201 = courseFactory.getCourse("BIOL", 201);
        aSection = new Section("201", "TR", "14:00", "15:20", new Building("BioSci", new LatLon(49.263920, -123.251552)));
        biol201.addSection(aSection);
        aSection.setCourse(biol201);
    }

}
