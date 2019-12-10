package redditorscannerbot;

import redditjackal.entities.*;
import redditjackal.entities.inbox.InboxMessage;
import redditjackal.exceptions.RedditorNotFoundException;
import redditjackal.exceptions.WrongCredentialsException;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MINUTES;

public class RedditorScannerBot {
    private List<String> credentials = Utils.getAllLinesFromFile("Bot.token");
    public Reddit reddit;

    private ScheduledExecutorService EXECUTOR = Executors
            .newScheduledThreadPool(1);

    public static String constructLink(Thing thing, String type)  {
        if (type.equals("post"))  {
            Post post = (Post) thing;
            return "[" + post.getLink().substring(post.getLink().indexOf("/r/")) + "]" +
                    "(" + post.getLink() + ")";
        }
        else if (type.equals("comment"))  {
            Comment comment = (Comment) thing;
            return "[" + comment.getLink().substring(comment.getLink().indexOf("/r/")) + "]" +
                    "(" + comment.getLink() + ")";
        }
        else if (type.equals("parentComment"))  {
            Comment parentComment = (Comment) thing;
            return "[" +
            parentComment.getLink().substring(
                    Utils.nthIndexOf(parentComment.getLink(), '/', 7)) + "](" +
                    parentComment.getLink() + ")";
        }

        return "no link";
    }

    public static List<String> makeReply(List<Post> postsFound, List<CommentWrapper> commentsFound,
                          String username, String keyword) {
        List<String> result = new ArrayList<>();

        String replyHeader = "**Redditor**: /u/" + username + "\n\n";
        replyHeader += "**Keyword**: *\"" + keyword + "\"*\n\n";
        replyHeader += "**Total posts found:** " + postsFound.size() + "\n\n";
        replyHeader += "**Total comments found:** " + commentsFound.size() + "\n\n";


        int thingsToSend = 0;
        String postsHeader = "**Posts:**\n\n";
        String reply = replyHeader + postsHeader;
        for (int i=0; i<postsFound.size(); i++) {
            Post post = postsFound.get(i);
            reply += (i+1) + ") " + constructLink(post, "post") +
                    " *contains* **_" + keyword + "_**" + "\n\n";
            thingsToSend++;
            if (thingsToSend>=25)  {
                result.add(reply);
                reply = replyHeader + postsHeader;
                thingsToSend = 0;
            }
        }

        String commentsHeader = "**Comments:**\n\n";
        reply = reply + commentsHeader;
        for (int i=0; i<commentsFound.size(); i++) {
            CommentWrapper commentWrapper = commentsFound.get(i);

            boolean commentNotNull = false;
            if (commentWrapper.getComment()!=null) {
                Comment comment = commentWrapper.getComment();
                reply += (i+1) + ") " + constructLink(comment, "comment") +
                        " *contains* **_" + keyword + "_**" + "\n\n";
                commentNotNull = true;
            }

            Comment parentComment = commentWrapper.getParentComment();
            if (parentComment != null)  {
                if (!commentNotNull)  {
                    reply += (i+1) + ") ";
                }
                else {
                    reply += "   ";
                }

                reply += "----> **parent**: " + constructLink(parentComment, "parentComment") +
                        " *contains* **_" + keyword + "_**" + "\n\n";
            }

            thingsToSend++;
            if (thingsToSend>=25)  {
                result.add(reply);
                reply = replyHeader + commentsHeader;
                thingsToSend = 0;
            }
        }

        if (thingsToSend>0)  {
            result.add(reply);
        }

        return result;
    }

    private Runnable redditTask  = new Runnable() {
        @Override
        public void run() {
            System.out.println("Started run");
            try {
                BotOwner admin = reddit.getMe();
                List<InboxMessage> unread = admin.getUnreadInboxMessages();

                for (InboxMessage message : unread) {
                    System.out.println("Found message: " + message.getBody());
                    admin.readPrivateMessage(message.getName());
                    String body = message.getBody();
                    String[] params = body.split(" ");

                    if (params.length < 2) {
                        return;
                    }

                    String username;
                    String keyword = body.substring(body.indexOf(" ") + 1);

                    if (body.startsWith("/u/") && params.length == 2) {
                        username = params[0].substring(3);
                    } else {
                        username = params[0];
                    }

                    Redditor redditor;
                    try {
                        redditor = reddit.getRedditor(username);
                    } catch (RedditorNotFoundException e) {
                        e.printStackTrace();
                        admin.sendPrivateMessage(message.getSubject(),
                                "User " + username + " doesn't exist", message.getAuthor());
                        return;
                    }

                    List<Comment> comments = redditor.commentHistory().updateNew(1000).getComments();
                    List<Post> posts = redditor.postHistory().updateNew(1000).getPosts();


                    List<Post> postsFound = new ArrayList<>();
                    List<CommentWrapper> commentsFound = new ArrayList<>();
                    for (int i = 0; i < posts.size(); i++) {
                        Post post = posts.get(i);
                        if (post.getTitle().toLowerCase().contains(keyword.toLowerCase()) ||
                                post.getBody().toLowerCase().contains(keyword.toLowerCase())) {
                            postsFound.add(post);
                        }
                    }

                    System.out.println("comments.size(): " + comments.size());
                    int commentCountForRateLimit = 0;
                    int countParent = 0;
                    for (int i = 0; i < comments.size(); i++) {
                        if (commentCountForRateLimit >= 120) {
                            System.out.println("too many requests, sleeping");

                            try {
                                Thread.sleep(60 * 1000);  //sleep 1 minute
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }

                            commentCountForRateLimit = 0;
                        }
                        commentCountForRateLimit++;

                        Comment comment = comments.get(i);
                        CommentWrapper commentWrapper = new CommentWrapper();
                        System.out.println(comment.getName());

                        boolean commentContains = false;
                        String commentText = comment.getBody();
                        if (commentText.toLowerCase().contains(keyword.toLowerCase())) {
                            commentWrapper.setComment(comment);
                            commentContains = true;
                        }

                        Comment parentComment = comment.getParentComment();
                        boolean parentContains = false;
                        if (parentComment != null) {
                            System.out.println("there is a parent: " +
                                    (++countParent) + parentComment.getName());
                            System.out.println(parentComment.getBody());
                            if (parentComment.getBody().
                                    toLowerCase().contains(keyword.toLowerCase())) {
                                parentContains = true;
                                commentWrapper.setParentComment(parentComment);
                            }
                        } else {
                            System.out.println("no parent: " + (++countParent));
                        }

                        if (commentContains || parentContains)  {
                            commentsFound.add(commentWrapper);
                        }
                    }

                    List<String> replies = makeReply(postsFound, commentsFound, username, keyword);
                    System.out.println();
                    for (String reply: replies)  {
                        admin.sendPrivateMessage(message.getSubject(), reply, message.getAuthor());
                        System.out.println("Sent reply: " + reply);
                        System.out.println("Waiting before sending the next reply...");
                        Thread.sleep(30*1000);
                    }
                }

                System.out.println("Ended run");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    {
        try  {
            reddit = new Reddit(credentials.get(0), credentials.get(1), credentials.get(2), credentials.get(3));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        EXECUTOR.scheduleAtFixedRate(redditTask, 0, 1, MINUTES);
    }

    public static void main(String[] args) {
        RedditorScannerBot bot = new RedditorScannerBot();
    }
}