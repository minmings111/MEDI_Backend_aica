from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

from googleapiclient.errors import HttpError

import config
from api_manager import APIKeyManager
from data_processor import VideoDataProcessor


class ChannelCommentFetcher:
    """기존 영상 데이터 기반으로 댓글을 수집하는 유틸리티."""

    def __init__(
        self,
        api_manager: APIKeyManager,
        output_root: Path,
        *,
        max_comments_per_video: Optional[int],
        page_size: int,
        order: str,
        text_format: str = "html",
    ):
        if page_size < 1 or page_size > 100:
            raise ValueError("page_size 값은 1~100 사이여야 합니다.")

        self.api_manager = api_manager
        self.processor = VideoDataProcessor()
        self.output_root = output_root
        self.output_root.mkdir(parents=True, exist_ok=True)

        self.max_comments_per_video = max_comments_per_video
        self.page_size = page_size
        self.order = order
        self.text_format = text_format

    # --------------------------------------------------------------------- #
    # 비디오 ID 로드
    # --------------------------------------------------------------------- #
    def _load_video_data(self, channel_id: str) -> Dict[str, Any]:
        """채널의 영상 데이터를 로드합니다.

        우선순위:
        1. `output/video_data/videos_{channel_id}.json`
        2. `output/channel_highlights/**/all_videos_{channel_id}.json`
        """
        video_data_dir = config.Config.OUTPUT_DIR / "video_data"
        video_data_file = video_data_dir / f"videos_{channel_id}.json"

        if video_data_file.exists():
            with open(video_data_file, "r", encoding="utf-8") as f:
                return json.load(f)

        highlights_root = config.Config.OUTPUT_DIR / "channel_highlights"
        if highlights_root.exists():
            pattern = f"all_videos_{channel_id}.json"
            for candidate in highlights_root.glob(f"**/{pattern}"):
                with open(candidate, "r", encoding="utf-8") as f:
                    data = json.load(f)
                print(f"ℹ️  채널 하이라이트 데이터 사용: {candidate}")
                return data

        raise FileNotFoundError(
            f"채널 ID {channel_id}에 대한 영상 데이터가 없습니다.\n"
            "먼저 영상 데이터를 수집한 뒤 다시 실행하세요."
        )

    def _extract_video_ids(self, video_data: Dict[str, Any]) -> List[str]:
        videos = video_data.get("videos", [])
        video_ids = []

        for video in videos:
            video_id = video.get("video_id") or video.get("id")
            if video_id:
                video_ids.append(video_id)

        if not video_ids:
            raise ValueError("영상 데이터에서 video_id를 찾을 수 없습니다.")

        return video_ids

    # --------------------------------------------------------------------- #
    # 댓글 수집
    # --------------------------------------------------------------------- #
    def fetch_comments_for_video(self, video_id: str) -> List[Dict[str, Any]]:
        """단일 영상의 댓글을 모두 수집합니다."""
        collected: List[Dict[str, Any]] = []
        next_page_token: Optional[str] = None
        units_used = 0

        try:
            while True:
                if (
                    self.max_comments_per_video is not None
                    and len(collected) >= self.max_comments_per_video
                ):
                    break

                max_results = self.page_size
                if self.max_comments_per_video is not None:
                    remaining = self.max_comments_per_video - len(collected)
                    if remaining <= 0:
                        break
                    max_results = min(max_results, remaining)

                request = self.api_manager.youtube_service.commentThreads().list(
                    part="snippet,replies",
                    videoId=video_id,
                    maxResults=max_results,
                    pageToken=next_page_token,
                    order=self.order,
                    textFormat=self.text_format,
                )

                response = self.api_manager.execute_request(request.execute)
                units_used += 2  # commentThreads().list 호출 비용

                items = response.get("items", [])
                if not items:
                    break

                collected.extend(items)
                next_page_token = response.get("nextPageToken")

                if not next_page_token:
                    break

        except HttpError as error:
            reason = self._extract_error_reason(error)
            if reason in {"commentsDisabled", "disabledComments"}:
                print(f"   ℹ️  댓글이 비활성화된 영상입니다: {video_id}")
                collected = []
            else:
                print(f"   ⚠️  댓글 수집 중 오류 ({video_id}): {error}")
                collected = []
        except Exception as error:  # pylint: disable=broad-except
            print(f"   ⚠️  댓글 수집 중 예기치 못한 오류 ({video_id}): {error}")
            collected = []
        finally:
            if units_used:
                self.api_manager.record_usage(units_used)

        return collected

    @staticmethod
    def _extract_error_reason(error: HttpError) -> str:
        try:
            data = json.loads(error.content.decode("utf-8"))
            errors = data.get("error", {}).get("errors", [])
            if errors:
                return errors[0].get("reason", "")
        except Exception:  # pylint: disable=broad-except
            pass
        return ""

    # --------------------------------------------------------------------- #
    # 메인 처리
    # --------------------------------------------------------------------- #
    def process_channel(
        self,
        channel_id: str,
        *,
        force: bool = False,
        limit_videos: Optional[int] = None,
    ) -> Dict[str, List[Dict[str, Any]]]:
        """지정된 채널의 모든 영상 댓글을 수집하고 저장합니다."""
        video_data = self._load_video_data(channel_id)
        video_ids = self._extract_video_ids(video_data)

        if limit_videos is not None:
            video_ids = video_ids[:limit_videos]

        output_dir = self.output_root / channel_id
        output_dir.mkdir(parents=True, exist_ok=True)

        aggregated_path = (
            config.Config.OUTPUT_DIR
            / "comment_data"
            / f"comments_{channel_id}.json"
        )
        aggregated_path.parent.mkdir(parents=True, exist_ok=True)

        aggregated_data = self._load_existing_aggregated(aggregated_path)

        updated_comments: Dict[str, List[Dict[str, Any]]] = {}

        print(f"🎯 댓글 수집 시작 - 채널: {channel_id}, 영상 {len(video_ids)}개")

        for idx, video_id in enumerate(video_ids, start=1):
            per_video_path = output_dir / f"comments_{video_id}.json"
            if per_video_path.exists() and not force:
                print(
                    f"[{idx}/{len(video_ids)}] ⏭️  이미 존재하는 댓글 파일 건너뜀: {video_id}"
                )
                continue

            print(f"[{idx}/{len(video_ids)}] 💬 댓글 수집 중: {video_id}")
            threads = self.fetch_comments_for_video(video_id)
            if not threads:
                print(f"   ⚠️  댓글이 없거나 수집 실패: {video_id}")
                continue

            comments = [
                self.processor.extract_comment_info(thread) for thread in threads
            ]

            self._save_per_video(per_video_path, channel_id, video_id, comments)
            updated_comments[video_id] = comments
            aggregated_data.setdefault("comments", {})[video_id] = comments

        if updated_comments:
            aggregated_data["channel_id"] = channel_id
            self._save_aggregated(aggregated_path, aggregated_data)
            print(f"✅ 댓글 데이터 저장 완료: {aggregated_path}")
        else:
            print("ℹ️  새로 저장된 댓글이 없습니다.")

        return updated_comments

    @staticmethod
    def _load_existing_aggregated(path: Path) -> Dict[str, Any]:
        if path.exists():
            try:
                with open(path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except json.JSONDecodeError:
                print(f"⚠️  기존 댓글 파일 파싱 실패, 새로 생성합니다: {path}")
        return {"channel_id": "", "comments": {}}

    @staticmethod
    def _save_per_video(
        path: Path,
        channel_id: str,
        video_id: str,
        comments: List[Dict[str, Any]],
    ):
        data = {
            "channel_id": channel_id,
            "video_id": video_id,
            "comments": comments,
        }
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"   💾 저장 완료: {path}")

    @staticmethod
    def _save_aggregated(path: Path, data: Dict[str, Any]):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="기존 영상 정보를 이용해 YouTube 댓글을 수집합니다.",
    )
    parser.add_argument(
        "--channel-id",
        required=True,
        help="댓글을 수집할 채널 ID (예: UCxxxxxxxxxxxxxx)",
    )
    parser.add_argument(
        "--max-comments",
        type=int,
        default=None,
        help="영상당 최대 댓글 수 (기본값: 제한 없음)",
    )
    parser.add_argument(
        "--page-size",
        type=int,
        default=100,
        help="API 요청당 가져올 최대 댓글 수 (1~100, 기본값 100)",
    )
    parser.add_argument(
        "--order",
        choices=("relevance", "time"),
        default="relevance",
        help="댓글 정렬 기준 (relevance | time)",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="이미 존재하는 댓글 파일이 있어도 다시 수집합니다.",
    )
    parser.add_argument(
        "--limit-videos",
        type=int,
        default=None,
        help="앞에서부터 N개의 영상에 대해서만 댓글을 수집합니다.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=config.Config.OUTPUT_DIR / "comment_data_by_video",
        help="댓글 JSON을 저장할 루트 디렉토리",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None):
    args = parse_args(argv)

    config.Config.ensure_directories()

    try:
        api_keys = config.Config.load_api_keys()
    except Exception as error:  # pylint: disable=broad-except
        print(f"❌ API 키 로드 실패: {error}")
        return 1

    api_manager = APIKeyManager(api_keys)

    fetcher = ChannelCommentFetcher(
        api_manager,
        output_root=args.output_dir,
        max_comments_per_video=args.max_comments,
        page_size=args.page_size,
        order=args.order,
    )

    try:
        fetcher.process_channel(
            args.channel_id,
            force=args.force,
            limit_videos=args.limit_videos,
        )
    except FileNotFoundError as error:
        print(f"❌ {error}")
        print("📌 먼저 영상 데이터를 수집한 뒤 다시 실행해주세요.")
        return 1
    except Exception as error:  # pylint: disable=broad-except
        print(f"❌ 댓글 수집 중 오류가 발생했습니다: {error}")
        return 1

    print("🎉 댓글 수집을 완료했습니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())


