package Model.Data;

import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.idan.hadad.checkitout.CheckItOutApp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import Model.Constants;
import Model.Item.Item;
import Model.Item.ItemCounter;
import Model.User.User;
import Model.User.UserModel;
import Model.User.UserModelInterface;

public class DataModel {

    private static final String DATA_MODEL_TAG = "DATA_MODEL_TAG";

    public interface SignoutDoneInterface {
        // true for success
        void done(boolean resultStatus);
    }

    public interface SetCurrentUserInterface {
        // true for success
        void done(boolean resultStatus);
    }

    private GoogleApiClient mGoogleApiClient;
    private User user;
    private Location userLocation;
    private String userAlgoKey;

    private final static DataModel instance = new DataModel();

    public HashMap<Location, ArrayList<ItemCounter>> getAlgoMap() {
        return algoMap;
    }

    private HashMap<Location, ArrayList<ItemCounter>> algoMap;

    private DataModel() {
        userLocation = null;
    }

    public static DataModel getInstance() {
        return instance;
    }

    public User getCurrentUser() {
        return user;
    }

    public Location getUserLocation() {
        return userLocation;
    }

    public void setUserLocation(Location userLocation) {
        this.userLocation = userLocation;
    }

    public void setCurrentUser(GoogleApiClient googleApiClient, final GoogleSignInAccount googleSignInAccount, final SetCurrentUserInterface listener) {
        Log.d(DATA_MODEL_TAG, "setCurrentUser: " + googleSignInAccount.getId());
        mGoogleApiClient = googleApiClient;
        userAlgoKey = Constants.ALGO_FILE_NAME + googleSignInAccount.getId();
        loadMap(); // load items for current user

        UserModel.getInstance().getUser(googleSignInAccount.getId(), new UserModelInterface.GetUserInterface() {
            @Override
            public void done(User resultUser) {
                if (resultUser != null) {
                    user = resultUser;
                    UserModel.getInstance().getImage(user.getUserId(), new UserModelInterface.GetImageInterface() {
                        @Override
                        public void done(byte[] imageData) {
                            if (imageData != null) {
                                user.setImageByte(imageData);
                            }
                            listener.done(true);
                        }
                    });
                } else {
                    Log.d(DATA_MODEL_TAG, "Adding user for the first time");
                    final String photoUrl = (googleSignInAccount.getPhotoUrl() != null) ? googleSignInAccount.getPhotoUrl().toString() : null;
                    UrlToByteArrayConvertor.convert(photoUrl, new UrlToByteArrayConvertor.ConversionInterface() {
                        @Override
                        public void done(byte[] byteData) {

                            if (byteData != null) {
                                user = new User(googleSignInAccount.getId(), googleSignInAccount.getDisplayName(), photoUrl, byteData);
                            } else {
                                user = new User(googleSignInAccount.getId(), googleSignInAccount.getDisplayName(), photoUrl);
                            }
                            listener.done(true);
                            UserModel.getInstance().addUser(user, new UserModelInterface.AffectUserInterface() {
                                @Override
                                public void done(boolean resultStatus) {
                                    Log.d(DATA_MODEL_TAG, "UserModel.addUser result: " + resultStatus);

                                    if (user.getImageByte() != null)
                                        UserModel.getInstance().setImage(user.getUserId(), user.getImageByte(), new UserModelInterface.AffectUserInterface() {
                                            @Override
                                            public void done(boolean resultStatus) {
                                                Log.d(DATA_MODEL_TAG, "UserModel.setImage result: " + resultStatus);
                                            }
                                        });
                                }
                            });
                        }
                    });
                }
            }
        });
    }

    public void signOut(final SignoutDoneInterface listener) {
        Log.d(DATA_MODEL_TAG, "signOut");
        user = null;
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                listener.done(status.isSuccess());
            }
        });
    }

    private void saveMap() {
        Log.d(DATA_MODEL_TAG, "saveMap to file");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(CheckItOutApp.getContext().getFilesDir(), userAlgoKey));
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(algoMap);
            objectOutputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadMap() {
        Log.d(DATA_MODEL_TAG, "loadMap from file");
        try {
            FileInputStream fileInputStream = new FileInputStream(new File(CheckItOutApp.getContext().getFilesDir(), userAlgoKey));
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);

            algoMap = (HashMap) objectInputStream.readObject();
            objectInputStream.close();

        } catch (Exception e) {
            e.printStackTrace();
            algoMap = new HashMap<>();
        }
    }

    //adding items by location to the algorithm map
    public void addItemsToMap(final ArrayList<Item> itemList) {
        if (itemList != null) {
            Log.d(DATA_MODEL_TAG, "addItemsToMap - location: (" + userLocation.latitude + "," + userLocation.longitude + ")");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int index;
                    ItemCounter itemCount;
                    ArrayList<ItemCounter> itemCountList;

                    Location location = DistanceCalculator.getNearestLocation(userLocation, algoMap.keySet());
                    if (location != null && DistanceCalculator.distance(location, userLocation) <= Constants.DISTANCE_ALGO_KEY_THRESHOLD) {
                        // add to the closest location if the distance isn't too far.
                        itemCountList = algoMap.get(location);
                    } else {
                        // create new key with the current location if there wasn't any nearby
                        itemCountList = new ArrayList<>();
                        location = userLocation;
                    }

                    for (Item item : itemList) {
                        itemCount = new ItemCounter(item.getName(), item.getAmount());
                        index = itemCountList.indexOf(itemCount);

                        if (index > -1) {
                            itemCount = itemCountList.get(index);
                            itemCount.setCount(itemCount.getCount() + item.getAmount());
                            itemCountList.set(index, itemCount);
                        } else {
                            itemCountList.add(itemCount);
                        }
                    }

                    algoMap.put(location, itemCountList);
                    saveMap();
                }
            }).start();
        }
    }

    public ArrayList<Item> sortByPopularity(ArrayList<Item> itemsInTable) {
        ArrayList<Item> sortedItemList = new ArrayList<>();
        ArrayList<String> popularItemsNames = getPopularItemsNames();
        int index;
        for (String itemName : popularItemsNames) {
            Item tmp = new Item(itemName, 0, 0);
            index = itemsInTable.indexOf(tmp);
            if (index > -1) {
                sortedItemList.add(itemsInTable.get(index));
                itemsInTable.remove(index);
            }
        }
        sortedItemList.addAll(itemsInTable);
        return sortedItemList;
    }

    //return popular item list for closest location
    private ArrayList<String> getPopularItemsNames() {
        Location location = DistanceCalculator.getNearestLocation(userLocation, algoMap.keySet());
        ArrayList<String> itemNames = new ArrayList<>();
        if (location != null) {
            ArrayList<ItemCounter> popularItems = algoMap.get(location);
            Collections.sort(popularItems);
            Collections.reverse(popularItems);
            for (ItemCounter itemCounter : popularItems) {
                itemNames.add(itemCounter.getName());
            }
        }
        return itemNames;
    }
}
