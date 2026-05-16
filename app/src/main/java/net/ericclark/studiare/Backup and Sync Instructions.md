# Setting Up Your Personal Studiare Sync Backend

Studiare uses a **BYOB (Bring Your Own Backend)** model. This means your data is completely yours—stored, authenticated, and backed up inside your own personal Google Firebase project.

Follow these step-by-step instructions to create your database, configure security and authentication, and connect it to Studiare.

---

## Step 1: Create a Firebase Project

1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Create a project** (or **Add project** if you already have one).
3. Enter a project name (e.g., `My-Studiare-Backend`) and click **Continue**.
4. **Google Analytics:** You can safely turn Google Analytics *off* for this project unless you specifically want to track your own app usage metrics. Click **Create project**.
5. Wait for the setup to complete and click **Continue** to open your project dashboard.

---

## Step 2: Set Up Authentication (Google Sign-In)

To sync your data securely across multiple devices, Firebase needs to know who you are using your Google Account.

1. In the left-hand sidebar menu, click on **Build** and select **Authentication**.
2. Click the **Get started** button.
3. Under the **Sign-in method** tab, select **Google** from the list of Additional providers.
4. Click the switch to **Enable** Google authentication.
5. Choose a **Project support email** from the dropdown menu (this will be your own email address).
6. Click **Save**.

---

## Step 3: Create and Configure the Firestore Database

This is the secure cloud database where your card collections, decks, and review history are backed up.

1. In the left-hand sidebar menu, click on **Build** and select **Firestore Database**.
2. Click the **Create database** button.
3. **Location:** Select a cloud database location closest to you geographically for optimum performance, then click **Next**.
4. **Security Rules:** Select **Start in test mode** for now, then click **Create**.
5. Once your database is provisioned, switch to the **Rules** tab at the top of the Firestore screen.
6. Replace the existing rules completely with the following secure configuration. This ensures that you can only read and write data that belongs to your authenticated account ID:

```javascript
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    // Secure isolated path per authenticated user account
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```
7. Click the **Publish** button to make these security rules live.

---

## Step 4: Add an Android App and Download `google-services.json`

Now, you must register Studiare with your Firebase project so that the app has permission to establish a connection.

1. Click on the **Project Overview** gear icon ⚙️ at the top left of the sidebar and select **Project settings**.
2. Scroll down to the **Your apps** section at the bottom of the *General* tab.
3. Click the **Android icon** (🤖) to add an application.
4. Enter the exact Android package name for Studiare:
   ```text
   net.ericclark.studiare
   ```
5. *(Optional)* Enter an app nickname, such as `Studiare App`.
6. Click **Register app**.
7. Click the **Download google-services.json** button to save the configuration file onto your computer or mobile device.
8. Click **Next** through the remaining steps in the Firebase wizard (you do not need to add the build SDK steps manually as Studiare already contains them), and click **Continue to console**.

---

## Step 5: Import the Configuration into Studiare

1. Transfer or save the downloaded `google-services.json` file to your Android device.
2. Open **Studiare** on your device.
3. Navigate to **Settings** from the side menu drawer.
4. Expand the **Backup & Sync** section.
5. Tap the **Set up Backup & Sync** button.
6. Select your `google-services.json` file using the system document picker.
7. Once connected, tap **Connect Google Account** to sign in and initiate your first automated background cloud sync!