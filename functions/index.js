import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { logger } from 'firebase-functions';

initializeApp();
const db = getFirestore();

export const sendChatRoomMessageNotification = onDocumentCreated(
  "chat_rooms/{chatRoomId}/messages/{messageId}",
  async (event) => {
    const snapshot = event.data;
    const chatRoomId = event.params.chatRoomId;

    if (!snapshot) {
      logger.warn("No message data found.");
      return;
    }

    const message = snapshot.data();

    const chatRoomDoc = await db.collection("chat_rooms").doc(chatRoomId).get();
    if (!chatRoomDoc.exists) {
      logger.warn(`Chat room ${chatRoomId} not found.`);
      return;
    }

    const chatRoom = chatRoomDoc.data();
    const members = chatRoom.members || [];
    const chatRoomName = chatRoom.chatRoomName || "New Message";

    let senderFirstName = "Someone";
    if (message.senderId && message.senderId !== "system") {
      const senderDoc = await db.collection("users").doc(message.senderId).get();
      if (senderDoc.exists) {
        const senderData = senderDoc.data();
        senderFirstName = senderData.firstName || senderData.username || "Someone";
      }
    } else if (message.senderName) {
      senderFirstName = message.senderName;
    }

    const messageBody = message.text || (
      message.type === 'image' ? 'Sent a photo' :
      message.type === 'file' ? 'Sent a file' :
      message.type === 'set' ? 'Shared a set' :
      'You have a new message'
    );

    const notifications = members.map(async (memberId) => {
      if (memberId === message.senderId) return;

      const userDoc = await db.collection("users").doc(memberId).get();
      if (!userDoc.exists) return;

      const userData = userDoc.data();
      const fcmToken = userData.fcmToken;
      const prefs = userData.chatNotificationPrefs || {};

      if (!fcmToken || prefs[chatRoomId] === false) return;

      const payload = {
        notification: {
          title: chatRoomName,
          body: `${senderFirstName}: ${messageBody}`,
        },
        data: {
          chatRoomId: chatRoomId,
          senderFirstName: senderFirstName,
          messageBody: messageBody,
        },
        token: fcmToken,
      };

      try {
        await getMessaging().send(payload);
        logger.info(`Notification sent to ${memberId}`);
      } catch (err) {
        logger.error(`Failed to send to ${memberId}:`, err);
      }
    });

    await Promise.all(notifications);
  }
);
