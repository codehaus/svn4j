<?
include("feedcreator.class.php");
include("rss_util.php");

$cacheFile = $_SERVER["DOCUMENT_ROOT"] . "/svn/feed/rss20.cache";
                                           
$rss = new UniversalFeedCreator();
$rss->useCached("RSS1.0", $cacheFile);
$rss->title = "TMate JavaSVN";
$rss->description = "TMate JavaSVN Library Change Log";
$rss->link = "http://tmate.org/svn/";
$rss->syndicationURL = "http://tmate.org/" . $PHP_SELF;
$rss->author = "TMate Software"; 
$rss->editor = "TMate Software"; 
$rss->authorEmail = "support@tmatesoft.com"; 
$rss->editorEmail = "support@tmatesoft.com"; 

$repository = "http://72.9.228.230/svn/jsvn/tags/";
$contents = read_contents($repository);
if (!$contents) {
   echo $rss->createFeed();
   exit;
}

$items = publish_rss20($repository, $contents, "http://tmate.org/svn/");
for($i = count($items); $i >=0 && $i > count($items) - 5; $i--) {

     $item = $items[$i];

     $rssitem = new FeedItem();

     $rssitem->title  = $item["title"];
     $rssitem->source = $item["source"];
     $rssitem->link   = $item["link"];
     $rssitem->author = $item["author"];
     $rssitem->date   = $item["date"];
     $rssitem->authorEmail = "support@tmatesoft.com"; 
     $rssitem->editorEmail = "support@tmatesoft.com"; 

     $rssitem->description = $item["rss_description"];
     $rss->addItem($rssitem);
}

$rss->saveFeed("RSS1.0", $cacheFile);
readfile($cacheFile);

exit;
?>